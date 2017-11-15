// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import com.google.inject.name.Names;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;
import com.googlesource.gerrit.plugins.quota.Module.AbstractHolderCacheLoader;

class HttpModule extends CacheModule {
  static final String CACHE_NAME_RESTAPI_ACCOUNTID = "restapi_rate_limits_by_account";
  static final String CACHE_NAME_RESTAPI_REMOTEHOST = "restapi_rate_limits_by_ip";

  final String RESTAPI_LIMIT_EXCEEDED_MSG;

  @Inject
  HttpModule(PluginConfigFactory plugincf, @PluginName String pluginName) {
    final PluginConfig pc = plugincf.getFromGerritConfig(pluginName);
    RESTAPI_LIMIT_EXCEEDED_MSG =
        new RateMsgHelper(
                "REST API", pc.getString(RateMsgHelper.RESTAPI_CONFIGURABLE_MSG_ANNOTATION))
            .getMessageFormatMsgWithBursts();
  }

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), AllRequestFilter.class).to(RestApiRequestRateEnforcer.class);
    cache(CACHE_NAME_RESTAPI_ACCOUNTID, Account.Id.class, Module.Holder.class)
        .loader(RestApiLoaderAccountId.class);
    cache(CACHE_NAME_RESTAPI_REMOTEHOST, String.class, Module.Holder.class)
        .loader(RestApiLoaderRemoteHost.class);
    bindConstant()
        .annotatedWith(Names.named(RateMsgHelper.RESTAPI_CONFIGURABLE_MSG_ANNOTATION))
        .to(RESTAPI_LIMIT_EXCEEDED_MSG);
  }

  private static class RestApiLoaderAccountId extends AbstractHolderCacheLoader<Account.Id> {
    @Inject
    RestApiLoaderAccountId(GenericFactory userFactory, AccountLimitsFinder finder) {
      super(Type.RESTAPI, userFactory, finder);
    }
  }

  private static class RestApiLoaderRemoteHost extends AbstractHolderCacheLoader<String> {
    @Inject
    RestApiLoaderRemoteHost(SystemGroupBackend systemGroupBackend, AccountLimitsFinder finder) {
      super(Type.RESTAPI, systemGroupBackend, finder);
    }
  }
}
