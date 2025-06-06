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

public class TaskQuotaForTaskForQueue extends Semaphore {
  public static final Map<String, Set<String>> supportedTasks =
      Map.of("uploadpack", Set.of("git-upload-pack"));
  private final String task;
  private final String queue;

  public TaskQuotaForTaskForQueue(String queue, String task, int maxStart) {
    super(maxStart);
    this.queue = queue;
    this.task = task;
  }

  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queue)
        && supportedTasks.get(this.task).stream().anyMatch(t -> task.toString().startsWith(t));
  }

  public static Set<String> supportedTasks() {
    return supportedTasks.keySet();
  }
}
