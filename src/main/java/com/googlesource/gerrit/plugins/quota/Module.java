// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.quota;

import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.googlesource.gerrit.plugins.quota.QuotaResource.QUOTA_KIND;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue.TaskListener;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.quota.QuotaEnforcer;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.RateLimit;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class Module extends CacheModule {
  static final String CACHE_NAME_ACCOUNTID = "rate_limits_by_account";
  static final String CACHE_NAME_REMOTEHOST = "rate_limits_by_ip";

  private final String uploadpackLimitExceededMsg;

  @Inject
  Module(PluginConfigFactory plugincf, @PluginName String pluginName) {
    PluginConfig pc = plugincf.getFromGerritConfig(pluginName);
    uploadpackLimitExceededMsg =
        new RateMsgHelper(
                Type.UPLOADPACK, pc.getString(RateMsgHelper.UPLOADPACK_CONFIGURABLE_MSG_ANNOTATION))
            .getMessageFormatMsg();
  }

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), ProjectCreationValidationListener.class)
        .to(MaxRepositoriesQuotaValidator.class);
    DynamicSet.bind(binder(), QuotaEnforcer.class).to(MaxRepositorySizeQuota.class);
    DynamicSet.bind(binder(), ProjectDeletedListener.class).to(DeletionListener.class);
    DynamicSet.bind(binder(), GarbageCollectorListener.class).to(GCListener.class);
    DynamicSet.setOf(binder(), UsageDataEventCreator.class);
    DynamicSet.bind(binder(), UsageDataEventCreator.class).to(RepoSizeEventCreator.class);
    install(MaxRepositorySizeQuota.module());
    install(
        new RestApiModule() {
          @Override
          protected void configure() {
            DynamicMap.mapOf(binder(), QUOTA_KIND);
            get(PROJECT_KIND, "quota").to(GetQuota.class);
            child(CONFIG_KIND, "quota").to(GetQuotas.class);
          }
        });
    bind(Publisher.class).in(Scopes.SINGLETON);
    bind(PublisherScheduler.class).in(Scopes.SINGLETON);
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(PublisherScheduler.class);

    DynamicSet.bind(binder(), UploadValidationListener.class).to(RateLimitUploadListener.class);
    bindConstant()
        .annotatedWith(Names.named(RateMsgHelper.UPLOADPACK_CONFIGURABLE_MSG_ANNOTATION))
        .to(uploadpackLimitExceededMsg);

    bind(TaskListener.class).annotatedWith(Exports.named("TaskQuotas")).to(TaskQuotas.class);
  }

  static class Holder {
    static final Holder EMPTY = new Holder(null);
    private int burstPermits;
    private AtomicInteger gracePermits = new AtomicInteger(0);
    private RateLimiter l;

    Holder(RateLimiter l) {
      this.l = l;
    }

    private Holder(RateLimiter l, int burstPermits) {
      this(l);
      this.burstPermits = burstPermits;
      gracePermits.set(burstPermits);
    }

    RateLimiter get() {
      return l;
    }

    int getBurstPermits() {
      return burstPermits;
    }

    /**
     * The grace permits ensure that a burst of requests can be served as the first interaction with
     * Gerrit. Without the extra booked burst, particularly the Gerrit web interface would display
     * an unexpected error, except for inappropriately lax rate limits.
     *
     * @return false, once the grace permits have been spent
     */
    boolean hasGracePermits() {
      if (gracePermits.get() <= 0) return false;
      return gracePermits.getAndDecrement() > 0;
    }

    private static final Holder createWithBurstyRateLimiter(Optional<RateLimit> limit) {
      return new Holder(
          RateLimitUploadListener.createSmoothBurstyRateLimiter(
              limit.get().getRatePerSecond(), limit.get().getMaxBurstSeconds()),
          (int) (limit.get().getMaxBurstSeconds() * limit.get().getRatePerSecond()));
    }
  }

  private abstract static class AbstractHolderCacheLoader<Key> extends CacheLoader<Key, Holder> {
    protected AccountLimitsFinder finder;
    protected Type limitsConfigType;

    protected AbstractHolderCacheLoader(Type limitsConfigType, AccountLimitsFinder finder) {
      this.limitsConfigType = limitsConfigType;
      this.finder = finder;
    }

    protected final Holder createWithBurstyRateLimiter(Optional<RateLimit> limit) throws Exception {
      if (limit.isPresent()) {
        return Holder.createWithBurstyRateLimiter(limit);
      }
      return Holder.EMPTY;
    }
  }

  static class HolderCacheLoaderByAccountId extends AbstractHolderCacheLoader<Account.Id> {
    private GenericFactory userFactory;

    protected HolderCacheLoaderByAccountId(
        Type limitsConfigType,
        IdentifiedUser.GenericFactory userFactory,
        AccountLimitsFinder finder) {
      super(limitsConfigType, finder);
      this.userFactory = userFactory;
    }

    private final Holder createWithBurstyRateLimiter(Account.Id key) throws Exception {
      return createWithBurstyRateLimiter(
          finder.firstMatching(limitsConfigType, userFactory.create(key)));
    }

    @Override
    public final Holder load(Account.Id key) throws Exception {
      return createWithBurstyRateLimiter(key);
    }
  }

  static class HolderCacheLoaderByRemoteHost extends AbstractHolderCacheLoader<String> {
    private String anonymous;

    protected HolderCacheLoaderByRemoteHost(
        Type limitsConfigType, SystemGroupBackend systemGroupBackend, AccountLimitsFinder finder) {
      super(limitsConfigType, finder);
      this.anonymous = systemGroupBackend.get(SystemGroupBackend.ANONYMOUS_USERS).getName();
    }

    private final Holder createWithBurstyRateLimiter() throws Exception {
      return createWithBurstyRateLimiter(finder.getRateLimit(limitsConfigType, anonymous));
    }

    @Override
    public final Holder load(String key) throws Exception {
      return createWithBurstyRateLimiter();
    }
  }

  @Provides
  @Named(CACHE_NAME_ACCOUNTID)
  @Singleton
  public LoadingCache<Account.Id, Module.Holder> getLoadingCacheByAccountId(
      GenericFactory userFactory, AccountLimitsFinder finder) {
    return CacheBuilder.newBuilder()
        .build(new HolderCacheLoaderByAccountId(Type.UPLOADPACK, userFactory, finder));
  }

  @Provides
  @Named(CACHE_NAME_REMOTEHOST)
  @Singleton
  public LoadingCache<String, Module.Holder> getLoadingCacheByRemoteHost(
      SystemGroupBackend systemGroupBackend, AccountLimitsFinder finder) {
    return CacheBuilder.newBuilder()
        .build(new HolderCacheLoaderByRemoteHost(Type.UPLOADPACK, systemGroupBackend, finder));
  }
}
