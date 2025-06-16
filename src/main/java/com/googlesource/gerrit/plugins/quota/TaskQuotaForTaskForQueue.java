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

import com.google.gerrit.server.git.WorkQueue;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

public class TaskQuotaForTaskForQueue extends Semaphore {
  private static final Map<String, Set<String>> SUPPORTED_TASKS_BY_GROUP =
          Map.of("uploadpack", Set.of("git-upload-pack"));
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+)\\s+("
              + String.join("|", TaskQuotaForTaskForQueue.supportedTasks())
              + ")\\s+(.+)");
  private final String taskGroup;
  private final String queue;

  public TaskQuotaForTaskForQueue(String queue, String taskGroup, int maxStart) {
    super(maxStart);
    this.queue = queue;
    this.taskGroup = taskGroup;
  }

  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queue)
        && SUPPORTED_TASKS_BY_GROUP.get(this.taskGroup).stream()
            .anyMatch(t -> task.toString().startsWith(t));
  }

  public static Set<String> supportedTasks() {
    return SUPPORTED_TASKS_BY_GROUP.keySet();
  }
}
