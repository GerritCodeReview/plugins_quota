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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskQuotaForTaskForQueue extends TaskQuotaForTask {
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+)\\s+(" + String.join("|", SUPPORTED_TASKS_BY_GROUP.keySet()) + ")\\s+(.+)");
  private final String queueName;

  public TaskQuotaForTaskForQueue(String queueName, String taskGroup, int maxStart) {
    super(taskGroup, maxStart);
    this.queueName = queueName;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return super.isApplicable(task) && task.getQueueName().equals(queueName);
  }

  public static Optional<TaskQuota> build(BuildInfo buildInfo) {
    Matcher matcher = CONFIG_PATTERN.matcher(buildInfo.config());
    if (matcher.matches()) {
      return Optional.of(
          new TaskQuotaForTaskForQueue(
              matcher.group(3), matcher.group(2), Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry [{}]", buildInfo.config());
      return Optional.empty();
    }
  }
}
