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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.git.WorkQueue.TaskMonitor;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

@Singleton
public class TaskQuotas implements TaskMonitor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Rule implements TaskMonitor {
    private final Semaphore quota;
    private String nameMatch;

    public Rule(String nameMatch, int quota) {
      this.nameMatch = nameMatch;
      this.quota = new Semaphore(quota, true);
    }

    @Override
    public void beforeRunning(Task task) {
      if (task.toString().matches(nameMatch)) {
        try {
          quota.acquire();
        } catch (InterruptedException e) {
        }
      }
    }

    @Override
    public void afterRunning(Task task) {
      if (task.toString().matches(nameMatch)) {
        quota.release();
      }
    }
  }

  private final List<TaskMonitor> monitors = new ArrayList<>();

  public TaskQuotas() {
    monitors.add(new Rule("git-upload-pack .*", 1));
    monitors.add(new Rule("gerrit query .*", 1));
  }

  @Override
  public void beforeRunning(Task task) {
    for (TaskMonitor monitor : monitors) {
      monitor.beforeRunning(task);
    }
  }

  @Override
  public void afterRunning(Task task) {
    for (TaskMonitor monitor : monitors) {
      monitor.afterRunning(task);
    }
  }
}
