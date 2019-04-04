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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class PublisherScheduler implements LifecycleListener {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final WorkQueue workQueue;
  private final Publisher publisher;
  private final Optional<ScheduleConfig.Schedule> scheduleConfig;

  @Inject
  PublisherScheduler(WorkQueue workQueue, Publisher publisher, @GerritServerConfig Config cfg) {
    this.workQueue = workQueue;
    this.publisher = publisher;
    scheduleConfig =
        ScheduleConfig.builder(cfg, "plugin")
            .setSubsection("quota")
            .setKeyInterval("publicationInterval")
            .setKeyStartTime("publicationStartTime")
            .buildSchedule();
  }

  @Override
  public void start() {
    if (!scheduleConfig.isPresent()) {
      log.atInfo().log("Ignoring missing schedule configuration");
    } else {
      ScheduleConfig.Schedule schedule = scheduleConfig.get();
      workQueue
          .getDefaultQueue()
          .scheduleAtFixedRate(
              publisher, schedule.initialDelay(), schedule.interval(), TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void stop() {}
}
