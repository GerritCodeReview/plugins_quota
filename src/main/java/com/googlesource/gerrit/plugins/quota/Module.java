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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.googlesource.gerrit.plugins.quota.QuotaResource.QUOTA_KIND;

import com.google.common.base.Optional;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.internal.UniqueAnnotations;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.RateLimit;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.PostReceiveHook;

class Module extends CacheModule {
  static final String CACHE_NAME_ACCOUNTID = "rate_limits_by_account";
  static final String CACHE_NAME_REMOTEHOST = "rate_limits_by_ip";

  @Override
  protected void configure() {
    bind(Config.class).annotatedWith(QuotaConfig.class)
        .toProvider(ConfigProvider.class).asEagerSingleton();
    DynamicSet.bind(binder(), ProjectCreationValidationListener.class)
        .to(MaxRepositoriesQuotaValidator.class);
    DynamicSet.bind(binder(), ReceivePackInitializer.class)
        .to(MaxRepositorySizeQuota.class);
    DynamicSet.bind(binder(), PostReceiveHook.class)
        .to(MaxRepositorySizeQuota.class);
    DynamicSet.bind(binder(), ProjectDeletedListener.class).to(
        DeletionListener.class);
    DynamicSet.bind(binder(), GarbageCollectorListener.class).to(
        GCListener.class);
    DynamicSet.setOf(binder(), UsageDataEventCreator.class);
    install(MaxRepositorySizeQuota.module());
    install(new RestApiModule() {
      @Override
      protected void configure() {
        DynamicMap.mapOf(binder(), QUOTA_KIND);
        get(PROJECT_KIND, "quota").to(GetQuota.class);
        child(CONFIG_KIND, "quota").to(GetQuotas.class);
      }
    });
    bind(Publisher.class).in(Scopes.SINGLETON);
    bind(PublisherScheduler.class).in(Scopes.SINGLETON);
    bind(ProjectNameResolver.class).in(Scopes.SINGLETON);
    bind(LifecycleListener.class)
      .annotatedWith(UniqueAnnotations.create())
      .to(PublisherScheduler.class);

    DynamicSet.bind(binder(), UploadValidationListener.class)
        .to(RateLimitUploadListener.class);
    cache(CACHE_NAME_ACCOUNTID, Account.Id.class, Holder.class)
        .loader(LoaderAccountId.class);
    cache(CACHE_NAME_REMOTEHOST, String.class, Holder.class)
        .loader(LoaderRemoteHost.class);
  }

  static class Holder {
    public static final Holder EMPTY = new Holder(null);
    private RateLimiter l;

    public Holder(RateLimiter l) {
      this.l = l;
    }

    public RateLimiter get() {
      return l;
    }
  }

  private static class LoaderAccountId extends CacheLoader<Account.Id, Holder> {
    private GenericFactory userFactory;
    private AccountLimitsFinder finder;

    @Inject
    LoaderAccountId(IdentifiedUser.GenericFactory userFactory,
        AccountLimitsFinder finder) {
      this.userFactory = userFactory;
      this.finder = finder;
    }

    @Override
    public Holder load(Account.Id key) throws Exception {
      IdentifiedUser user = userFactory.create(key);
      Optional<RateLimit> limit =
          finder.firstMatching(AccountLimitsConfig.Type.UPLOADPACK, user);
      if (limit.isPresent()) {
        return new Holder(RateLimitUploadListener.createSmoothBurstyRateLimiter(
            limit.get().getRatePerSecond(), limit.get().getMaxBurstSeconds()));
      }
      return Holder.EMPTY;
    }
  }

  private static class LoaderRemoteHost extends CacheLoader<String, Holder> {
    private AccountLimitsFinder finder;
    private String anonymous;

    @Inject
    LoaderRemoteHost(SystemGroupBackend systemGroupBackend,
        AccountLimitsFinder finder) {
      this.finder = finder;
      this.anonymous = systemGroupBackend.get(ANONYMOUS_USERS).getName();
    }

    @Override
    public Holder load(String key) throws Exception {
      Optional<RateLimit> limit =
          finder.getRateLimit(AccountLimitsConfig.Type.UPLOADPACK, anonymous);
      if (limit.isPresent()) {
        return new Holder(RateLimitUploadListener.createSmoothBurstyRateLimiter(
            limit.get().getRatePerSecond(), limit.get().getMaxBurstSeconds()));
      }
      return Holder.EMPTY;
    }
  }
}
