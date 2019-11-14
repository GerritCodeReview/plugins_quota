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
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.git.WorkQueue.TaskListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.eclipse.jgit.lib.Config;

@Singleton
public class TaskQuotas implements TaskListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class MutableSemaphore extends Semaphore {
    public MutableSemaphore(int permits, boolean fair) {
      super(permits, fair);
    }

    public void setPermits(int p) {
      if (availablePermits() != p) {
        setPermitsSynchronized(p);
      }
    }

    private synchronized void setPermitsSynchronized(int p) {
      int cur = availablePermits();
      if (cur > p) {
        reducePermits(cur - p);
      }
      if (cur > p) {
        release(p - cur);
      }
    }
  }

  public static class Rule implements TaskListener {
    private final MutableSemaphore quota;
    private final String nameMatch;

    public Rule(String nameMatch, int quota) {
      this.nameMatch = nameMatch;
      this.quota = new MutableSemaphore(quota, true);
    }

    public void setQuota(int q) {
      quota.setPermits(q);
    }

    @Override
    public void beforeRunning(Task<?> task) {
      if (task.toString().matches(nameMatch)) {
        try {
          quota.acquire();
        } catch (InterruptedException e) {
        }
      }
    }

    @Override
    public void afterRunning(Task<?> task) {
      if (task.toString().matches(nameMatch)) {
        quota.release();
      }
    }
  }

  private final QuotaFinder quotaFinder;
  private final Map<String, Map<String, Rule>> rulesByTaskNameByNamespace = new HashMap<>();
  private final Map<Task, Collection<Rule>> rulesByTask = new ConcurrentHashMap<>();

  @Inject
  public TaskQuotas(QuotaFinder quotaFinder) {
    this.quotaFinder = quotaFinder;
  }

  @Override
  public void beforeRunning(Task<?> task) {
    Collection<Rule> listeners = getListeners(task);
    if (listeners != null) {
      logger.atSevere().log("listeners " + listeners);

      rulesByTask.put(task, listeners);
      listeners.stream().forEach(listener -> listener.beforeRunning(task));
    }
  }

  @Override
  public void afterRunning(Task<?> task) {
    Collection<Rule> listeners = rulesByTask.get(task);
    if (listeners != null) {
      listeners.stream().forEach(listener -> listener.afterRunning(task));
    }
  }

  private Collection<Rule> getListeners(Task<?> task) {
    Config cfg = updateRules();
    String namespace = "*";
    if (task instanceof ProjectTask) {
      Project.NameKey project = ((ProjectTask<?>) task).getProjectNameKey();
      if (project != null) {
        namespace = quotaFinder.firstNamespace(cfg, project);
      }
    }
    if (namespace != null) {
      Map<String, Rule> rulesByTaskName = rulesByTaskNameByNamespace.get(namespace);
      if (rulesByTaskName != null) {
        return rulesByTaskName.values();
      }
    }
    return null;
  }

  private Config updateRules() {
    Config cfg = quotaFinder.getConfig(); // Keep a snapshot corresponding to our rules;
    for (QuotaSection quotaSection : quotaFinder.all(cfg)) {
      String namespace = quotaSection.getNamespace();
      for (Map.Entry<String, Integer> e : quotaSection.getMaxByTaskname().entrySet()) {
        String taskName = e.getKey();
        int quota = e.getValue();

        Rule rule = null;
        Map<String, Rule> rulesByTaskName = rulesByTaskNameByNamespace.get(namespace);
        if (rulesByTaskName != null) {
          rule = rulesByTaskName.get(taskName);
          if (rule != null) {
            rule.setQuota(quota);
          }
        }
        if (rulesByTaskName == null) {
          rulesByTaskName = new HashMap<>();
          rulesByTaskNameByNamespace.put(namespace, rulesByTaskName);
        }
        if (rule == null) {
          rulesByTaskName.put(taskName, new Rule(taskName, quota));
        }
      }
    }
    return cfg;
  }
}
