// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.truth.OptionalSubject;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(name = "quota", sysModule = "com.googlesource.gerrit.plugins.quota.Module")
public class QuotaPluginIT extends LightweightPluginDaemonTest {
  private static final String GROUP_LIMITED_ON_UPLOADPACK = "groupLimitedOnUploadPack";
  private static final String GROUP_LIMITED_ON_RESTAPI = "groupLimitedOnRestApi";
  private static final int UPLOADPACK_LIMIT = 100;
  private static final int UPLOADPACK_BURST = 1;
  private static final int RESTAPI_LIMIT = 200;
  private static final int RESTAPI_BURST = 2;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Before
  public void setup() throws Exception {
    addUserToGroup(user, GROUP_LIMITED_ON_UPLOADPACK);
    addUserToGroup(user, GROUP_LIMITED_ON_RESTAPI);

    try (TestRepository<InMemoryRepository> allProjectsRepo = cloneProject(allProjects)) {
      GitUtil.fetch(allProjectsRepo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
      Ref configRef = allProjectsRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      allProjectsRepo.reset(configRef.getObjectId());
      pushFactory
          .create(
              admin.newIdent(),
              allProjectsRepo,
              "Set quota",
              "quota.config",
              "[group \""
                  + GROUP_LIMITED_ON_UPLOADPACK
                  + "\"]\n"
                  + "uploadpack = "
                  + UPLOADPACK_LIMIT
                  + "/s burst "
                  + UPLOADPACK_BURST * UPLOADPACK_LIMIT
                  + "\n"
                  + "[group \""
                  + GROUP_LIMITED_ON_RESTAPI
                  + "\"]\n"
                  + "restapi = "
                  + RESTAPI_LIMIT
                  + "/s burst "
                  + RESTAPI_BURST * RESTAPI_LIMIT
                  + "\n")
          .to(RefNames.REFS_CONFIG)
          .assertOkStatus();
    }
  }

  @Test
  public void pluginCanLoad() throws Exception {}

  @Test
  public void userShouldBeLimitedOnUploadPacks() {
    asserUserLimitedOn(
        Type.UPLOADPACK, GROUP_LIMITED_ON_UPLOADPACK, UPLOADPACK_LIMIT, UPLOADPACK_BURST);
  }

  @Test
  public void userShouldBeLimitedOnRestApi() {
    asserUserLimitedOn(Type.RESTAPI, GROUP_LIMITED_ON_RESTAPI, RESTAPI_LIMIT, RESTAPI_BURST);
  }

  @Test
  public void adminShouldNotBeLimitedOnRestApi() {
    assertAdminUserHasNoLimitsOn(Type.RESTAPI);
  }

  @Test
  public void adminShouldNotBeLimitedOnUploadPack() {
    assertAdminUserHasNoLimitsOn(Type.UPLOADPACK);
  }

  private void assertAdminUserHasNoLimitsOn(Type type) {
    AccountLimitsFinder accountLimitsFinder = getAccountLimitsFinder();
    OptionalSubject.assertThat(accountLimitsFinder.getRateLimit(type, "Administrators")).isEmpty();
    OptionalSubject.assertThat(accountLimitsFinder.firstMatching(type, userFactory.create(admin.id()))).isEmpty();
  }

  private void asserUserLimitedOn(
      Type limitType, String groupName, int ratePerSecLimit, int burstSecs) {
    AccountLimitsFinder accountLimitsFinder = getAccountLimitsFinder();
    Optional<AccountLimitsConfig.RateLimit> groupLimit =
        accountLimitsFinder.getRateLimit(limitType, groupName);
    OptionalSubject.assertThat(groupLimit).isPresent();
    assertRateLimits(groupLimit.get(), limitType, ratePerSecLimit, burstSecs);

    Optional<AccountLimitsConfig.RateLimit> rateLimit =
        accountLimitsFinder.firstMatching(limitType, userFactory.create(user.id()));
    OptionalSubject.assertThat(rateLimit).isPresent();

    assertRateLimitsAreEqual(rateLimit.get(), groupLimit.get());
  }

  private void addUserToGroup(TestAccount user, String groupLimitedOnUploadpack)
      throws RestApiException {
    GroupApi groupApi = gApi.groups().create(groupLimitedOnUploadpack);
    groupApi.removeMembers(admin.username());
    groupApi.addMembers(user.username());
  }

  private AccountLimitsFinder getAccountLimitsFinder() {
    return plugin.getSysInjector().getInstance(AccountLimitsFinder.class);
  }

  private static void assertRateLimitsAreEqual(
      AccountLimitsConfig.RateLimit rateLimit, AccountLimitsConfig.RateLimit groupLimit) {
    assertThat(rateLimit.getRatePerSecond()).isEqualTo(groupLimit.getRatePerSecond());
    assertThat(rateLimit.getMaxBurstSeconds()).isEqualTo(groupLimit.getMaxBurstSeconds());
    assertThat(rateLimit.getType()).isEqualTo(groupLimit.getType());
  }

  private static void assertRateLimits(
      AccountLimitsConfig.RateLimit rateLimit, Type rateType, int ratePerSecond, int burstSeconds) {
    assertThat(rateLimit.getRatePerSecond()).isEqualTo(ratePerSecond);
    assertThat(rateLimit.getMaxBurstSeconds()).isEqualTo(burstSeconds);
    assertThat(rateLimit.getType()).isEqualTo(rateType);
  }
}
