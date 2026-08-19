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

public class TaskQuotaForTaskForUserForQueue extends TaskQuotaForTaskForQueue {
  public static final String KEY = "maxStartForTaskForUserForQueue";

  private final String user;

  public TaskQuotaForTaskForUserForQueue(
      QuotaSection quotaSection, String queueName, String user, String taskGroup, int maxStart) {
    super(quotaSection, queueName, taskGroup, maxStart);
    this.user = user;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return TaskParser.isUser(task, user) && super.isApplicable(task);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    return TaskForUserForQueueConfig.build(qs, cfg, KEY, TaskQuotaForTaskForUserForQueue::new);
  }

  @Override
  public String toString() {
    return KEY
        + ": task [%s], queue [%s], user [%s], permits [%d], namespace [%s]"
            .formatted(taskGroup, queueName, user, maxPermits, quotaSection.getNamespace());
  }
}
