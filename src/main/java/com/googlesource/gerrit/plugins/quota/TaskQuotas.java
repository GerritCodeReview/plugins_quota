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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.git.WorkQueue.TaskMonitor;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Singleton
public class TaskQuotas implements TaskMonitor {
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

  private final QuotaFinder quotaFinder;
  private final ListMultimap<QuotaSection, TaskMonitor> monitorsByQuotaSection =
      ArrayListMultimap.create();
  private final ListMultimap<String, TaskMonitor> monitorsByNamespace = ArrayListMultimap.create();

  private QuotaSection all;
  private Map<Task, List<TaskMonitor>> monitorsByTask = new ConcurrentHashMap<>();

  @Inject
  public TaskQuotas(QuotaFinder quotaFinder) {
    this.quotaFinder = quotaFinder;
    for (QuotaSection quotaSection : quotaFinder.all()) {
      String namespace = quotaSection.getNamespace();
      if (namespace.equals("*")) {
        all = quotaSection;
      }
      for (Map.Entry<String, Integer> e : quotaSection.getMaxByTaskname().entrySet()) {
        monitorsByNamespace.put(namespace, new Rule(e.getKey(), e.getValue()));
      }
    }
  }

  @Override
  public void beforeRunning(Task task) {
    List<TaskMonitor> monitors = getMonitors(task);
    if (monitors != null) {
      monitorsByTask.put(task, monitors);
      monitors.stream().forEach(monitor -> monitor.beforeRunning(task));
    }
  }

  @Override
  public void afterRunning(Task task) {
    List<TaskMonitor> monitors = monitorsByTask.get(task);
    if (monitors != null) {
      monitors.stream().forEach(monitor -> monitor.afterRunning(task));
    }
  }

  private List<TaskMonitor> getMonitors(Task task) {
    if (task instanceof ProjectTask) {
      Project.NameKey project = ((ProjectTask) task).getProjectNameKey();
      if (project != null) {
        return monitorsByNamespace.get(quotaFinder.firstNamespace(project));
      }
    }
    if (all != null) {
      return monitorsByNamespace.get(all.getNamespace());
    }
    return null;
  }
}
