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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoftMaxForTaskForQueue implements TaskQuota {
  public static final Logger log = LoggerFactory.getLogger(SoftMaxForTaskForQueue.class);
  public static final String KEY = "softMaxStartForTaskForQueue";

  private final QuotaSection quotaSection;
  private final String taskGroup;
  private final String queueName;
  private final int softMax;
  private final TaskGroup group;
  private final QueueManager.Queue queue;
  private final AtomicInteger runningTasks = new AtomicInteger(0);

  public SoftMaxForTaskForQueue(
      QuotaSection quotaSection, String queueName, String taskGroup, int softMax) {
    this.quotaSection = quotaSection;
    this.queueName = queueName;
    this.taskGroup = taskGroup;
    this.softMax = softMax;
    this.group = new TaskGroup(taskGroup);
    this.queue = QueueManager.Queue.fromKey(queueName);
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equalsIgnoreCase(queueName) && group.isApplicable(task);
  }

  @Override
  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    AtomicBoolean acquired = new AtomicBoolean(false);
    runningTasks.updateAndGet(
        count -> {
          if (count < softMax || QueueManager.ensureIdle(queue, 1)) {
            acquired.set(true);
            return count + 1;
          }
          return count;
        });
    return acquired.get();
  }

  @Override
  public void onStop(WorkQueue.Task<?> task) {
    runningTasks.updateAndGet(count -> count > 0 ? count - 1 : 0);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = TaskQuotaForTaskForQueue.CONFIG_PATTERN.matcher(cfg);
    if (matcher.matches()) {
      return Optional.of(
          new SoftMaxForTaskForQueue(
              qs, matcher.group(3), matcher.group(2), Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry for softMaxStartForTaskForQueue [{}]", cfg);
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return KEY
        + ": task [%s], queue [%s], softMax [%d], namespace [%s]"
        .formatted(taskGroup, queueName, softMax, quotaSection.getNamespace());
  }
}
