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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublisherScheduler implements LifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(PublisherScheduler.class);
  private final WorkQueue workQueue;
  private final Publisher publisher;
  private final ScheduleConfig scheduleConfig;

  @Inject
  PublisherScheduler(WorkQueue workQueue, Publisher publisher, @GerritServerConfig Config cfg) {
    this.workQueue = workQueue;
    this.publisher = publisher;
    scheduleConfig =
        new ScheduleConfig(cfg, "plugin", "quota", "publicationInterval", "publicationStartTime");
  }

  @Override
  public void start() {
    long interval = scheduleConfig.getInterval();
    long delay = scheduleConfig.getInitialDelay();
    if (delay == MISSING_CONFIG && interval == MISSING_CONFIG) {
      log.info("Ignoring missing schedule configuration");
    } else if (delay < 0 || interval <= 0) {
      log.warn("Ignoring invalid schedule configuration");
    } else {
      workQueue
          .getDefaultQueue()
          .scheduleAtFixedRate(publisher, delay, interval, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void stop() {}
}
