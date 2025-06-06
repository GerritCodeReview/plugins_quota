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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TaskQuotas implements WorkQueue.TaskParker {
  private static final Logger log = LoggerFactory.getLogger(TaskQuotas.class);
  private final QuotaFinder quotaFinder;
  private final List<TaskQuotaForTaskForQueue> quotas = new ArrayList<>();
  private final Map<Integer, List<TaskQuotaForTaskForQueue>> permitsByTask =
      new ConcurrentHashMap<>();

  @Inject
  public TaskQuotas(QuotaFinder quotaFinder) {
    this.quotaFinder = quotaFinder;
    initQuotas();
  }

  private void initQuotas() {
    quotas.addAll(quotaFinder.getGlobalNamespacedQuota().getMaxStartForTaskForQueue());
  }

  @Override
  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    List<TaskQuotaForTaskForQueue> acquiredQuotas = new ArrayList<>();
    for (TaskQuotaForTaskForQueue quota : quotas) {
      if (quota.isApplicable(task)) {
        if (!quota.tryAcquire()) {
          log.debug("Task [{}] will be parked due task quota rules", task);
          acquiredQuotas.forEach(Semaphore::release);
          return false;
        }
        acquiredQuotas.add(quota);
      }
    }

    permitsByTask.put(task.getTaskId(), acquiredQuotas);
    return true;
  }

  @Override
  public void onNotReadyToStart(WorkQueue.Task<?> task) {
    release(task);
  }

  @Override
  public void onStart(WorkQueue.Task<?> task) {}

  @Override
  public void onStop(WorkQueue.Task<?> task) {
    release(task);
  }

  private void release(WorkQueue.Task<?> task) {
    Optional.ofNullable(permitsByTask.remove(task.getTaskId()))
        .ifPresent(permits -> permits.forEach(Semaphore::release));
  }
}
