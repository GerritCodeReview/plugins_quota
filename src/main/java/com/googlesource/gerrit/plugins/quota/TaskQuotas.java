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
  private List<TaskQuota> quotas;
  private Map<Integer, List<TaskQuota>> permitsByTask;

  @Inject
  public TaskQuotas(QuotaFinder quotaFinder) {
    this.quotaFinder = quotaFinder;
    quotas = new ArrayList<>();
    permitsByTask = new ConcurrentHashMap<>();

    initQuotas();
  }

  private void initQuotas() {
    quotas.addAll(quotaFinder.getGlobalNamespacedQuota().getMaxStartForTaskForQueue());
  }

  @Override
  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    List<TaskQuota> applicableQuotas = quotas.stream().filter(q -> q.isApplicable(task)).toList();
    if (applicableQuotas.isEmpty()) {
      return true;
    }

    List<TaskQuota> acquiredQuotas =
        applicableQuotas.stream().filter(Semaphore::tryAcquire).toList();
    if (applicableQuotas.size() == acquiredQuotas.size()) {
      permitsByTask.put(task.getTaskId(), applicableQuotas);
      return true;
    } else {
      log.debug("Task [{}] will be parked because due task quota rules", task);
      acquiredQuotas.forEach(Semaphore::release);
      return false;
    }
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
    if (permitsByTask.containsKey(task.getTaskId())) {
      permitsByTask.get(task.getTaskId()).forEach(Semaphore::release);
      permitsByTask.remove(task.getTaskId());
    }
  }
}
