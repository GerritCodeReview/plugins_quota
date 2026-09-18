// Copyright (C) 2026 The Android Open Source Project
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
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.RefNames;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

@TestPlugin(name = "quota", sysModule = "com.googlesource.gerrit.plugins.quota.Module")
public class TaskQuotaUserIT extends LightweightPluginDaemonTest {
  private static final String QUEUE = "SSH-Interactive-Worker";

  @Test
  public void configuredUserIsMatchedAsStoredOnTheAccount() throws Exception {
    setQuotaConfig(user.username());

    assertThat(configuredUser()).isEqualTo(user.username());
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  public void differentlyCasedUserIsMatchedAsStoredWhenGerritIgnoresCase() throws Exception {
    setQuotaConfig(user.username().toUpperCase(Locale.US));

    assertThat(configuredUser()).isEqualTo(user.username());
  }

  @Test
  public void differentlyCasedUserIsKeptAsConfiguredWhenGerritHonoursCase() throws Exception {
    String configured = user.username().toUpperCase(Locale.US);
    setQuotaConfig(configured);

    assertThat(configuredUser()).isEqualTo(configured);
  }

  @Test
  public void userWithoutAnAccountIsKeptAsConfigured() throws Exception {
    setQuotaConfig("no-such-account");

    assertThat(configuredUser()).isEqualTo("no-such-account");
  }

  /** Returns the user the single configured quota ended up matching on. */
  private String configuredUser() {
    QuotaFinder quotaFinder = plugin.getSysInjector().getInstance(QuotaFinder.class);
    TaskQuotaKeys taskQuotaKeys = plugin.getSysInjector().getInstance(TaskQuotaKeys.class);

    List<TaskQuota> quotas = taskQuotaKeys.buildQuotas(quotaFinder.firstMatching(project));
    assertThat(quotas).hasSize(1);

    String quota = quotas.get(0).toString();
    Matcher user = Pattern.compile("user \\[([^]]*)]").matcher(quota);
    assertThat(user.find()).isTrue();
    return user.group(1);
  }

  private void setQuotaConfig(String user) throws Exception {
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
              """
              [quota "%s"]
                maxStartForTaskForUserForQueue = 1 uploadpack %s %s
              """
                  .formatted(project.get(), user, QUEUE))
          .to(RefNames.REFS_CONFIG)
          .assertOkStatus();
    }
  }
}
