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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskQuotaPerUserForTaskForQueue extends TaskQuotaForTaskForQueue {
  public static final Logger log = LoggerFactory.getLogger(TaskQuotaPerUserForTaskForQueue.class);
  private final PerUserTaskQuota perUserTaskQuota;

  public TaskQuotaPerUserForTaskForQueue(String queue, String taskGroup, int maxStart) {
    super(queue, taskGroup, maxStart);
    perUserTaskQuota = new PerUserTaskQuota(maxStart);
  }

  @Override
  public boolean tryAcquire(WorkQueue.Task<?> task) {
    return perUserTaskQuota.tryAcquire(task);
  }

  @Override
  public void release(WorkQueue.Task<?> task) {
    perUserTaskQuota.release(task);
  }

  public static Optional<TaskQuota> build(String cfg) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    if (matcher.matches()) {
      return Optional.of(
          new TaskQuotaPerUserForTaskForQueue(
              matcher.group(3), matcher.group(2), Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry [{}]", cfg);
      return Optional.empty();
    }
  }
}
