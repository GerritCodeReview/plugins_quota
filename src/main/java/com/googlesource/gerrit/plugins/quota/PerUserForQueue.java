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
import java.util.regex.Pattern;

/**
 * Base class of the quotas that count each user separately on one queue, whatever the task.
 *
 * <p>Each one counts the same way and differs only in what it makes of the count, which is what
 * {@link #canStart} decides.
 */
public abstract class PerUserForQueue implements TaskQuota {
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile("(\\d+)\\s+(" + String.join("|", QueueManager.Queue.keys()) + ")");

  protected final QuotaSection quotaSection;
  protected final int limit;
  protected final QueueManager.Queue queue;
  private final PerUserTaskQuota perUserTaskQuota;

  protected PerUserForQueue(QuotaSection quotaSection, int limit, String queueName) {
    this.quotaSection = quotaSection;
    this.limit = limit;
    this.queue = QueueManager.Queue.fromKey(queueName);
    this.perUserTaskQuota = new PerUserTaskQuota((ids, task) -> canStart(ids.size()));
  }

  /** Whether a user already running this many tasks on the queue may start one more. */
  protected abstract boolean canStart(int running);

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queue.getName());
  }

  @Override
  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    return perUserTaskQuota.tryAcquire(task);
  }

  @Override
  public void onStop(WorkQueue.Task<?> task) {
    perUserTaskQuota.release(task);
  }
}
