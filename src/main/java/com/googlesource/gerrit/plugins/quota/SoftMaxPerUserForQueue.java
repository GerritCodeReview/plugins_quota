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

import static com.googlesource.gerrit.plugins.quota.TaskParser.user;

import com.google.gerrit.server.git.WorkQueue;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoftMaxPerUserForQueue extends TaskQuota {
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile("(\\d+)\\s+(" + String.join("|", QueueStats.Queue.keys()) + ")");
  private final int softMax;
  private final QueueStats.Queue queue;
  private final ConcurrentHashMap<String, Integer> taskStartedCountByUser =
      new ConcurrentHashMap<>();

  public SoftMaxPerUserForQueue(int softMax, String queueName) {
    this.softMax = softMax;
    this.queue = QueueStats.Queue.fromKey(queueName);
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queue.getName());
  }

  @Override
  public boolean tryAcquire(WorkQueue.Task<?> task) {
    return user(task)
        .map(
            user -> {
              AtomicBoolean acquired = new AtomicBoolean(false);
              taskStartedCountByUser.compute(
                  user,
                  (key, val) -> {
                    int runningTasks = (val != null) ? val : 0;
                    if (runningTasks < softMax || QueueStats.acquire(queue, 1)) {
                      acquired.setPlain(true);
                      QueueStats.release(queue, 1);
                      ++runningTasks;
                    }
                    return runningTasks;
                  });
              return acquired.getPlain();
            })
        .orElse(true);
  }

  @Override
  public void release(WorkQueue.Task<?> task) {
    user(task)
        .ifPresent(
            user ->
                taskStartedCountByUser.computeIfPresent(
                    user,
                    (u, tasks) -> {
                      return tasks == 1 ? null : --tasks;
                    }));
  }

  public static Optional<TaskQuota> build(String cfg) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    return matcher.find()
        ? Optional.of(
            new SoftMaxPerUserForQueue(Integer.parseInt(matcher.group(1)), matcher.group(2)))
        : Optional.empty();
  }
}
