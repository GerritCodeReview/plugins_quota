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

import com.google.gerrit.entities.Project;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Config;

public class QuotaSection {
  public static final String QUOTA = "quota";
  public static final String KEY_MAX_PROJECTS = "maxProjects";
  public static final String KEY_MAX_REPO_SIZE = "maxRepoSize";
  public static final String KEY_MAX_TOTAL_SIZE = "maxTotalSize";
  public static final String KEY_MAX_START_FOR_TASK_FOR_QUEUE = "maxStartForTaskForQueue";

  private final Config cfg;
  private final String namespace;
  private final Namespace resolvedNamespace;

  QuotaSection(Config cfg, String namespace) {
    this(cfg, namespace, namespace);
  }

  QuotaSection(Config cfg, String namespace, String resolvedNamespace) {
    this.cfg = cfg;
    this.namespace = namespace;
    this.resolvedNamespace = new Namespace(resolvedNamespace);
  }

  public String getNamespace() {
    return resolvedNamespace.get();
  }

  public boolean matches(Project.NameKey project) {
    return resolvedNamespace.matches(project);
  }

  public Integer getMaxProjects() {
    if (!cfg.getNames(QUOTA, namespace).contains(KEY_MAX_PROJECTS)) {
      return null;
    }
    return cfg.getInt(QUOTA, namespace, KEY_MAX_PROJECTS, Integer.MAX_VALUE);
  }

  public Long getMaxRepoSize() {
    if (!cfg.getNames(QUOTA, namespace).contains(KEY_MAX_REPO_SIZE)) {
      return null;
    }
    return cfg.getLong(QUOTA, namespace, KEY_MAX_REPO_SIZE, Long.MAX_VALUE);
  }

  public Long getMaxTotalSize() {
    if (!cfg.getNames(QUOTA, namespace).contains(KEY_MAX_TOTAL_SIZE)) {
      return null;
    }
    return cfg.getLong(QUOTA, namespace, KEY_MAX_TOTAL_SIZE, Long.MAX_VALUE);
  }

  public List<TaskQuotaForTaskForQueue> getMaxStartForTaskForQueue() {
    if (!cfg.getNames(QUOTA, namespace).contains(KEY_MAX_START_FOR_TASK_FOR_QUEUE)) {
      return null;
    }

    String[] vals = cfg.getStringList(QUOTA, namespace, KEY_MAX_START_FOR_TASK_FOR_QUEUE);
    return Arrays.stream(vals)
        .map(
            val -> {
              int firstSpace = val.indexOf(" ");
              int secondSpace = val.indexOf(" ", firstSpace + 1);

              Integer max = Integer.valueOf(val.substring(0, firstSpace));
              String task = val.substring(firstSpace + 1, secondSpace);
              String queue = val.substring(secondSpace + 1);
              return new TaskQuotaForTaskForQueue(queue, task, max);
            })
        .toList();
  }
}
