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

import com.google.gerrit.server.git.WorkQueue;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskQuotaForTaskForQueueForUser extends TaskQuotaForTaskForQueue {
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+)\\s+("
              + String.join("|", SUPPORTED_TASKS_BY_GROUP.keySet())
              + ")\\s+([a-zA-Z0-9]+)"
              + "\\s+(.+)");
  public static final Pattern USER_EXTRACT_PATTERN = Pattern.compile("\\(([a-z0-9]+)\\)$");
  private final String user;

  public TaskQuotaForTaskForQueueForUser(
      String queueName, String user, String taskGroup, int maxStart) {
    super(queueName, taskGroup, maxStart);
    this.user = user;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    Matcher taskUser = USER_EXTRACT_PATTERN.matcher(task.toString());
    return taskUser.find() && user.equals(taskUser.group(1)) && super.isApplicable(task);
  }

  public static Optional<TaskQuota> build(String config) {
    Matcher matcher = CONFIG_PATTERN.matcher(config);
    if (matcher.matches()) {
      return Optional.of(
          new TaskQuotaForTaskForQueueForUser(
              matcher.group(4),
              matcher.group(3),
              matcher.group(2),
              Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry [{}]", config);
      return Optional.empty();
    }
  }
}
