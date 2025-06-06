// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.server.project.ProjectCache;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class TaskBudgetConfig {
  private static final String TASK_BUDGET_SECTION = "taskBudget";
  private static final String QUOTA_CFG_FILE = "quota.config";
  private static final String QUEUE = "queue";
  private static final String TASK = "task";
  private static final String START_MAX = "startMax";
  private final ProjectCache projectCache;

  @Inject
  public TaskBudgetConfig(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  Collection<TaskBudget> getBudgets() {
    Config cfg = projectCache.getAllProjects().getConfig(QUOTA_CFG_FILE).get();
    Set<String> subSections = cfg.getSubsections(TASK_BUDGET_SECTION);

    if (subSections.isEmpty()) {
      return Set.of();
    }

    Set<TaskBudget> taskBudgets = new HashSet<>();
    for (String subSection : subSections) {
      String[] queues = cfg.getStringList(TASK_BUDGET_SECTION, subSection, QUEUE);
      String task = cfg.getString(TASK_BUDGET_SECTION, subSection, TASK);
      String rule = subSection.replaceAll(" ", "_");
      int startMax = cfg.getInt(TASK_BUDGET_SECTION, subSection, START_MAX, -1);

      taskBudgets.add(new TaskBudget(rule, Arrays.asList(queues), task, startMax));
    }

    return taskBudgets;
  }
}
