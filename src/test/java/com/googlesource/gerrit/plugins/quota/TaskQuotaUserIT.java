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

import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.inject.Inject;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Test;

/**
 * Asserts which spellings of a configured username limit an account, by running upload-packs over
 * SSH against a maxStartForTaskForUserForQueue of one and observing which of them get parked.
 */
@UseSsh
@Sandboxed
@TestPlugin(name = "quota", sysModule = "com.googlesource.gerrit.plugins.quota.Module")
public class TaskQuotaUserIT extends LightweightPluginDaemonTest {
  /**
   * The batch queue, as the acceptance framework pins the interactive one to a single thread, which
   * leaves no room for a second uploadpack to be parked next to a running one.
   */
  private static final String QUEUE = "SSH-Batch-Worker";

  private static final String BATCH_THREADS = "4";
  private static final String UPLOAD_PACK = "git-upload-pack";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Inject private WorkQueue workQueue;
  @Inject private PluginGuiceEnvironment pluginEnv;

  private final List<Reader> uploadPacks = new ArrayList<>();

  @After
  public void endUploadPacks() throws Exception {
    for (Reader uploadPack : uploadPacks) {
      uploadPack.close();
    }
    userSshSession.close();
    if (!awaitNoUploadPacks()) {
      for (Task<?> task : uploadPackTasks()) {
        @SuppressWarnings("unused")
        boolean unused = task.cancel(true);
      }
    }
  }

  @Test
  @GerritConfig(name = "sshd.batchThreads", value = BATCH_THREADS)
  public void configuredUserIsLimited() throws Exception {
    limitUploadPacksTo(user.username());

    startUploadPack();
    startUploadPack();

    assertUploadPacksEventually(1, Task.State.RUNNING);
    assertUploadPacksEventually(1, Task.State.PARKED);
  }

  @Test
  @GerritConfig(name = "sshd.batchThreads", value = BATCH_THREADS)
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  public void differentlyCasedUserIsLimitedWhenGerritIgnoresCase() throws Exception {
    limitUploadPacksTo(user.username().toUpperCase(Locale.US));

    startUploadPack();
    startUploadPack();

    assertUploadPacksEventually(1, Task.State.RUNNING);
    assertUploadPacksEventually(1, Task.State.PARKED);
  }

  @Test
  @GerritConfig(name = "sshd.batchThreads", value = BATCH_THREADS)
  public void differentlyCasedUserIsNotLimitedWhenGerritHonoursCase() throws Exception {
    limitUploadPacksTo(user.username().toUpperCase(Locale.US));

    startUploadPack();
    startUploadPack();

    assertUploadPacksEventually(2, Task.State.RUNNING);
  }

  @Test
  @GerritConfig(name = "sshd.batchThreads", value = BATCH_THREADS)
  public void userWithoutAnAccountIsNotLimited() throws Exception {
    limitUploadPacksTo("no-such-account");

    startUploadPack();
    startUploadPack();

    assertUploadPacksEventually(2, Task.State.RUNNING);
  }

  /**
   * Allows the given username a single concurrent uploadpack, and starts the plugin on that
   * configuration, which it reads once while loading.
   */
  private void limitUploadPacksTo(String user) throws Exception {
    configureAllProjects(user);

    plugin.stop(pluginEnv);
    pluginEnv.onStopPlugin(plugin);
    plugin.start(pluginEnv);
    pluginEnv.onStartPlugin(plugin);
  }

  /**
   * Starts an uploadpack for {@code user}. It keeps running until {@link #endUploadPacks()} closes
   * it, as the client on this end never negotiates.
   */
  private void startUploadPack() throws Exception {
    uploadPacks.add(userSshSession.execAndReturnReader(UPLOAD_PACK + " /" + project.get()));
  }

  private void assertUploadPacksEventually(int count, Task.State state) throws Exception {
    Instant deadline = Instant.now().plus(TIMEOUT);
    while (uploadPacksIn(state) != count) {
      assertWithMessage(
              "%s uploadpack(s) %s within %s seconds, states: %s",
              count, state, TIMEOUT.toSeconds(), uploadPackStates())
          .that(Instant.now().isBefore(deadline))
          .isTrue();
      MILLISECONDS.sleep(20);
    }
  }

  private List<Task.State> uploadPackStates() {
    return uploadPackTasks().stream().map(Task::getState).toList();
  }

  private boolean awaitNoUploadPacks() throws Exception {
    Instant deadline = Instant.now().plus(TIMEOUT);
    while (!uploadPackTasks().isEmpty()) {
      if (!Instant.now().isBefore(deadline)) {
        return false;
      }
      MILLISECONDS.sleep(20);
    }
    return true;
  }

  private long uploadPacksIn(Task.State state) {
    return uploadPackTasks().stream().filter(task -> state.equals(task.getState())).count();
  }

  private List<Task<?>> uploadPackTasks() {
    return workQueue.getTasks().stream()
        .filter(task -> task.toString().startsWith(UPLOAD_PACK))
        .toList();
  }

  /** Configures the quota, and the batch priority that puts uploadpacks on the queue it names. */
  private void configureAllProjects(String user) throws Exception {
    try (TestRepository<InMemoryRepository> allProjectsRepo = cloneProject(allProjects)) {
      GitUtil.fetch(allProjectsRepo, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
      Ref configRef = allProjectsRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      allProjectsRepo.reset(configRef.getObjectId());
      pushFactory
          .create(
              admin.newIdent(),
              allProjectsRepo,
              "Configure quota and batch priority",
              Map.of(
                  "quota.config",
                  """
                  [quota "%s"]
                    maxStartForTaskForUserForQueue = 1 uploadpack %s %s
                  """
                      .formatted(project.get(), user, QUEUE),
                  "project.config",
                  batchPriority(allProjectsRepo)))
          .to(RefNames.REFS_CONFIG)
          .assertOkStatus();
    }
  }

  /** Returns All-Projects' configuration, with every user's commands sent to the batch queue. */
  private String batchPriority(TestRepository<InMemoryRepository> allProjectsRepo)
      throws Exception {
    Repository repo = allProjectsRepo.getRepository();
    ObjectId config = repo.resolve(RefNames.REFS_CONFIG + ":project.config");
    return new String(repo.open(config).getBytes(), UTF_8)
        + """
          [capability]
            priority = batch group Registered Users
          """;
  }
}
