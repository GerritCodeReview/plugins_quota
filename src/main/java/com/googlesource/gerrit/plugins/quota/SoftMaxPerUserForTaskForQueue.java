// Copyright (C) 2026 The Android Open Source Project
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoftMaxPerUserForTaskForQueue extends SoftMaxPerUserForQueue {
  public static final Logger log = LoggerFactory.getLogger(SoftMaxPerUserForTaskForQueue.class);
  public static final String KEY = "softMaxStartPerUserForTaskForQueue";

  private final String taskGroup;
  private final TaskGroup group;

  public SoftMaxPerUserForTaskForQueue(
      QuotaSection quotaSection, String queueName, String taskGroup, int softMax) {
    super(quotaSection, softMax, queueName);
    this.taskGroup = taskGroup;
    this.group = new TaskGroup(taskGroup);
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return group.isApplicable(task) && super.isApplicable(task);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = TaskQuotaForTaskForQueue.CONFIG_PATTERN.matcher(cfg);
    if (matcher.matches()) {
      return Optional.of(
          new SoftMaxPerUserForTaskForQueue(
              qs, matcher.group(3), matcher.group(2), Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry for softMaxStartPerUserForTaskForQueue [{}]", cfg);
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return KEY
        + ": softMax [%d], task [%s], queue [%s], namespace [%s]"
            .formatted(softMax, taskGroup, queue.getName(), quotaSection.getNamespace());
  }
}
