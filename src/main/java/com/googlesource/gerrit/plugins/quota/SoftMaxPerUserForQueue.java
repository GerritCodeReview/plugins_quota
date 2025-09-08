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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoftMaxPerUserForQueue extends TaskQuota {
  public static final Map<String, Function<BuildInfo, Integer>> QUEUES =
      Map.of(
          "SSH-Interactive-Worker",
          BuildInfo::interactiveThreads,
          "SSH-Batch-Worker",
          BuildInfo::batchThreads);
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile("(\\d+)\\s+(" + String.join("|", QUEUES.keySet()) + ")");
  private final int softMax;
  private final String queueName;
  private final ConcurrentHashMap<String, Integer> taskStartedCountByUser =
      new ConcurrentHashMap<>();

  public SoftMaxPerUserForQueue(int maxPermits, int softMax, String queueName) {
    super(maxPermits);
    this.softMax = softMax;
    this.queueName = queueName;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queueName);
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
                    boolean overSoftLimit = runningTasks >= softMax;
                    int permitCost = overSoftLimit ? 2 : 1;

                    if (permits.tryAcquire(permitCost)) {
                      acquired.setPlain(true);
                      if (overSoftLimit) {
                        permits.release(1);
                      }
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
                      permits.release(1);
                      return tasks == 1 ? null : --tasks;
                    }));
  }

  public static Optional<TaskQuota> build(BuildInfo buildInfo) {
    Matcher matcher = CONFIG_PATTERN.matcher(buildInfo.config());
    return matcher.find()
        ? Optional.of(
            new SoftMaxPerUserForQueue(
                QUEUES.get(matcher.group(2)).apply(buildInfo),
                Integer.parseInt(matcher.group(1)),
                matcher.group(2)))
        : Optional.empty();
  }
}
