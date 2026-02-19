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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskQuotaForTaskForQueueForUser extends TaskQuotaForTaskForQueue {
  public static final Logger log = LoggerFactory.getLogger(TaskQuotaForTaskForQueueForUser.class);
  public static final String KEY = "maxStartForTaskForUserForQueue";
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+)\\s+("
              + String.join("|", SUPPORTED_TASKS_BY_GROUP.keySet())
              + ")\\s+("
              + TaskParser.USERNAME_CHARS
              + ")\\s+(.+)");
  private final String user;

  public TaskQuotaForTaskForQueueForUser(
      QuotaSection quotaSection, String queueName, String user, String taskGroup, int maxStart) {
    super(quotaSection, queueName, taskGroup, maxStart);
    this.user = user;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return TaskParser.user(task).map(user::equals).orElse(false) && super.isApplicable(task);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String config) {
    Matcher matcher = CONFIG_PATTERN.matcher(config);
    if (matcher.matches()) {
      return Optional.of(
          new TaskQuotaForTaskForQueueForUser(
              qs,
              matcher.group(4),
              matcher.group(3),
              matcher.group(2),
              Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry [{}]", config);
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return KEY
        + ": task [%s], queue [%s], user [%s], permits [%d], namespace [%s]"
            .formatted(taskGroup, queueName, user, maxPermits, quotaSection.getNamespace());
  }
}
