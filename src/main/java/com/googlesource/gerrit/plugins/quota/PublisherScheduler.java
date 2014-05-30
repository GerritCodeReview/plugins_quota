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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import java.util.concurrent.TimeUnit;

public class PublisherScheduler implements LifecycleListener {

  private static final int INITIAL_DELAY = 1;
  private final WorkQueue workQueue;
  private final Publisher publisher;
  private final int period;

  @Inject
  PublisherScheduler(WorkQueue workQueue, Publisher publisher,  PluginConfigFactory cfg) {
    this.workQueue = workQueue;
    this.publisher = publisher;
    period = cfg.getFromGerritConfig("quota").getInt("publicationInterval", 0);
  }

  @Override
  public void start() {
    if (period < 1) {
      return;
    }
    workQueue.getDefaultQueue().scheduleWithFixedDelay(publisher, INITIAL_DELAY,
        period, TimeUnit.MINUTES);
  }

  @Override
  public void stop() {
  }

}
