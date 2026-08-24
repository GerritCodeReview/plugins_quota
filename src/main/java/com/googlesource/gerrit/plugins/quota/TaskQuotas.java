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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gerrit.server.git.WorkQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class TaskQuotas implements WorkQueue.TaskParker {
  private final QuotaFinder quotaFinder;
  private final ProjectResolver projectResolver;
  private final TaskQuotaKeys taskQuotaKeys;
  private final Map<Integer, List<TaskQuota>> quotasByTask = new ConcurrentHashMap<>();
  private final Map<QuotaSection, List<TaskQuota>> quotasByNamespace = new HashMap<>();
  private final List<TaskQuota> globalQuotas = new ArrayList<>();
  private final Config quotaConfig;
  private final int maxParked;

  @Inject
  public TaskQuotas(
      QuotaFinder quotaFinder,
      ProjectResolver projectResolver,
      TaskQuotaKeys taskQuotaKeys,
      @GerritServerConfig Config serverCfg,
      ThreadSettingsConfig threadSettingsConfig) {
    this.quotaFinder = quotaFinder;
    this.projectResolver = projectResolver;
    this.taskQuotaKeys = taskQuotaKeys;
    this.quotaConfig = quotaFinder.getQuotaConfig();
    this.maxParked = quotaConfig.getInt("global", null, "maxParked", 0);

    // Replicating this logic from the core
    int poolSize = threadSettingsConfig.getSshdThreads();
    int batchThreads =
        serverCfg.getInt("sshd", "batchThreads", threadSettingsConfig.getSshdBatchTreads());
    if (batchThreads > poolSize) {
      poolSize += batchThreads;
    }
    int interactiveThreads = Math.max(1, poolSize - batchThreads);
    QueueManager.initQueueWithCapacity(QueueManager.Queue.INTERACTIVE, interactiveThreads);
    QueueManager.initQueueWithCapacity(QueueManager.Queue.BATCH, batchThreads);

    initQuotas();
  }

  @VisibleForTesting
  public TaskQuotas(
      QuotaFinder quotaFinder,
      ProjectResolver projectResolver,
      TaskQuotaKeys taskQuotaKeys,
      int interactiveThreads,
      int batchThreads) {
    this.quotaFinder = quotaFinder;
    this.projectResolver = projectResolver;
    this.taskQuotaKeys = taskQuotaKeys;
    this.quotaConfig = quotaFinder.getQuotaConfig();
    this.maxParked = quotaConfig.getInt("global", null, "maxParked", 0);

    QueueManager.initQueueWithCapacity(QueueManager.Queue.INTERACTIVE, interactiveThreads);
    QueueManager.initQueueWithCapacity(QueueManager.Queue.BATCH, batchThreads);

    initQuotas();
  }

  private void initQuotas() {
    quotasByNamespace.putAll(
        quotaFinder.getQuotaNamespaces(quotaConfig).stream()
            .collect(Collectors.toMap(Function.identity(), taskQuotaKeys::buildQuotas)));
    globalQuotas.addAll(
        taskQuotaKeys.buildQuotas(quotaFinder.getGlobalNamespacedQuota(quotaConfig)));
  }

  @Override
  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    if (!QueueManager.acquire(task)) {
      if (shouldInterruptInsteadOfPark()) {
        ParkedQuotaTransitionLogger.logTaskInterruptedForMaxParked(task, maxParked);
        task.cancel(true);
        return true;
      }
      ParkedQuotaTransitionLogger.logTaskWithNoSatisfyingReservation(task);
      return false;
    }

    Optional<Project.NameKey> estimatedProject = projectResolver.estimateProject(task);
    List<TaskQuota> applicableQuotas = new ArrayList<>(globalQuotas);
    applicableQuotas.addAll(
        estimatedProject
            .map(
                project -> {
                  return quotasByNamespace.getOrDefault(
                      Optional.ofNullable(quotaFinder.firstMatching(quotaConfig, project))
                          .orElse(quotaFinder.getFallbackNamespacedQuota(quotaConfig)),
                      List.of());
                })
            .orElse(List.of()));

    List<TaskQuota> acquiredQuotas = new ArrayList<>();
    for (TaskQuota quota : applicableQuotas) {
      if (quota.isApplicable(task)) {
        if (!quota.isReadyToStart(task)) {
          QueueManager.release(task);
          acquiredQuotas.forEach(q -> q.onStop(task));
          if (shouldInterruptInsteadOfPark()) {
            ParkedQuotaTransitionLogger.logTaskInterruptedForMaxParked(task, maxParked);
            task.cancel(true);
            return true;
          }
          ParkedQuotaTransitionLogger.logTaskWithEnforcedQuota(task, quota);
          return false;
        }
        acquiredQuotas.add(quota);
      }
    }

    if (!acquiredQuotas.isEmpty()) {
      quotasByTask.put(task.getTaskId(), acquiredQuotas);
    }

    ParkedQuotaTransitionLogger.logOnTaskStartIfParked(task);
    return true;
  }

  private boolean shouldInterruptInsteadOfPark() {
    return maxParked > 0 && ParkedQuotaTransitionLogger.parkedCount() >= maxParked;
  }

  @Override
  public void onNotReadyToStart(WorkQueue.Task<?> task) {
    QueueManager.release(task);
    ParkedQuotaTransitionLogger.clear(task);
    Optional.ofNullable(quotasByTask.remove(task.getTaskId()))
        .ifPresent(quotas -> quotas.forEach(q -> q.onStop(task)));
  }

  @Override
  public void onStart(WorkQueue.Task<?> task) {}

  @Override
  public void onStop(WorkQueue.Task<?> task) {
    QueueManager.release(task);
    ParkedQuotaTransitionLogger.clear(task);
    Optional.ofNullable(quotasByTask.remove(task.getTaskId()))
        .ifPresent(quotas -> quotas.forEach(q -> q.onStop(task)));
  }
}
