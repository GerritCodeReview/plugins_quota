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

import static com.google.gerrit.server.config.ScheduleConfig.MISSING_CONFIG;

import com.google.common.io.CharStreams;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class PublisherScheduler implements LifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(PublisherScheduler.class);
  private final WorkQueue workQueue;
  private final Publisher publisher;
  private final ScheduleConfig scheduleConfig;

  private String[] gitVersionCommand;

  @Inject
  PublisherScheduler(WorkQueue workQueue,
      Publisher publisher,
      @GerritServerConfig Config cfg,
      PluginConfigFactory pCfg,
      @PluginName String pluginName) {
    this.workQueue = workQueue;
    this.publisher = publisher;
    scheduleConfig = new ScheduleConfig(cfg, "plugin", pluginName, "publicationInterval",
        "publicationStartTime");
    if (pCfg.getFromGerritConfig(pluginName).getBoolean("useGitObjectCount",
        false)) {
      String gitPath =
          pCfg.getFromGerritConfig(pluginName).getString("gitPath", "git");
      this.gitVersionCommand = new String[] {gitPath, "--version"};
    }
  }

  @Override
  public void start() {
    try {
      if (!validateGitCommand()) {
        throw new IllegalStateException("Wrong Git command!");
      }
    } catch (InterruptedException | IOException e) {
      throw new IllegalStateException("error occurred when checking git version: "
          + e.getMessage());
    }

    long interval = scheduleConfig.getInterval();
    long delay = scheduleConfig.getInitialDelay();
    if (delay == MISSING_CONFIG && interval == MISSING_CONFIG) {
      log.info("Ignoring missing schedule configuration");
    } else if (delay < 0 || interval <= 0) {
      log.warn("Ignoring invalid schedule configuration");
    } else {
      workQueue.getDefaultQueue().scheduleAtFixedRate(publisher, delay,
          interval, TimeUnit.MILLISECONDS);
    }
  }

  private boolean validateGitCommand()
      throws InterruptedException, IOException {
    if (gitVersionCommand == null) {
      return true;
    }

    ProcessBuilder builder = new ProcessBuilder(gitVersionCommand);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    process.waitFor();
    try (InputStreamReader isr =
        new InputStreamReader(process.getInputStream())) {
      String gitVersionRawOutput = CharStreams.toString(isr).trim();
      return gitVersionRawOutput != null
          && gitVersionRawOutput.contains("git version")
          && process.exitValue() == 0;
    }
  }

  @Override
  public void stop() {
  }

}
