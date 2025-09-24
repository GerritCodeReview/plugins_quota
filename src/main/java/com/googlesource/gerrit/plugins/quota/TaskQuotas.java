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

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gerrit.server.git.WorkQueue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TaskQuotas implements WorkQueue.TaskParker {
  private static final Logger log = LoggerFactory.getLogger(TaskQuotas.class);
  private final QuotaFinder quotaFinder;
  private final Map<Integer, List<TaskQuota>> quotasByTask = new ConcurrentHashMap<>();
  private final Map<QuotaSection, List<TaskQuota>> quotasByNamespace = new HashMap<>();
  private final List<TaskQuota> globalQuotas = new ArrayList<>();
  private final Pattern PROJECT_PATTERN = Pattern.compile("\\s+/?(.*)\\s+(\\(\\S+\\))$");
  private final Config quotaConfig;
  private final Map<Integer, Integer> onStopCalled = new ConcurrentHashMap<>();

  @Inject
  public TaskQuotas(
      QuotaFinder quotaFinder,
      @GerritServerConfig Config serverCfg,
      ThreadSettingsConfig threadSettingsConfig) {
    this.quotaFinder = quotaFinder;
    this.quotaConfig = quotaFinder.getQuotaConfig();

    // Replicating this logic from the core
    int poolSize = threadSettingsConfig.getSshdThreads();
    int batchThreads =
        serverCfg.getInt("sshd", "batchThreads", threadSettingsConfig.getSshdBatchTreads());
    if (batchThreads > poolSize) {
      poolSize += batchThreads;
    }
    int interactiveThreads = Math.max(1, poolSize - batchThreads);
    QueueStats.initQueueWithCapacity(QueueStats.Queue.INTERACTIVE, interactiveThreads);
    QueueStats.initQueueWithCapacity(QueueStats.Queue.BATCH, batchThreads);

    initQuotas();
  }

  private void initQuotas() {
    quotasByNamespace.putAll(
        quotaFinder.getQuotaNamespaces(quotaConfig).stream()
            .collect(Collectors.toMap(Function.identity(), QuotaSection::getAllQuotas)));
    globalQuotas.addAll(quotaFinder.getGlobalNamespacedQuota(quotaConfig).getAllQuotas());
  }

  @Override
  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    synchronized (task) {
      //      if (onStopCalled.containsKey(task.getTaskId()) || task.isCancelled()) {
      //        log.error(
      //            "improper isReadyToStart call: "
      //                + onStopCalled.containsKey(task.getTaskId())
      //                + " "
      //                + task.isCancelled());
      //        return false;
      //      }
      QueueStats.Queue queue = QueueStats.Queue.fromKey(task.getQueueName());
      if (!QueueStats.acquire(queue, 1)) {
        if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
          log.error(
              task.getTaskId()
                  + " isReadyToStart called with false returning by QueueStats. "
                  + task);
        return false;
      }

      Optional<Project.NameKey> estimatedProject = estimateProject(task);
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
            log.debug("Task [{}] will be parked due task quota rules", task);
            QueueStats.release(queue, 1);
            acquiredQuotas.forEach(q -> q.onStop(task));

            if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
              log.error(task.getTaskId() + " isReadyToStart called with false returning. " + task);
            return false;
          }
          acquiredQuotas.add(quota);
        }
      }

      if (!acquiredQuotas.isEmpty()) {
        quotasByTask.put(task.getTaskId(), acquiredQuotas);
      }

      if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
        log.error(
            task.getTaskId() + " isReadyToStart called with true returning. " + task.toString());
      return true;
    }
  }

  @Override
  public void onNotReadyToStart(WorkQueue.Task<?> task) {
    synchronized (task) {
      if (!quotasByTask.containsKey(task.getTaskId())) {
        if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
          log.error(task.getTaskId() + " improper onNotReadyToStart called. " + task);
        return;
      }
      if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
        log.error(task.getTaskId() + " onNotReadyToStart called. " + task.toString());
      QueueStats.release(QueueStats.Queue.fromKey(task.getQueueName()), 1);
      Optional.ofNullable(quotasByTask.remove(task.getTaskId()))
          .ifPresent(quotas -> quotas.forEach(q -> q.onStop(task)));
    }
  }

  @Override
  public void onStart(WorkQueue.Task<?> task) {
    synchronized (task) {
      if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
        log.error(task.getTaskId() + " onStart called.");
    }
  }

  @Override
  public void onStop(WorkQueue.Task<?> task) {
    synchronized (task) {
      //      if (!quotasByTask.containsKey(task.getTaskId())) {
      //        log.error(task.getTaskId() + " improper onStop called. " + task);
      //        return;
      //      }
      onStopCalled.put(task.getTaskId(), -1);
      if (task.getQueueName().equalsIgnoreCase(QueueStats.Queue.INTERACTIVE.getName()))
        log.error(task.getTaskId() + " onStop called. " + task.toString());
      QueueStats.release(QueueStats.Queue.fromKey(task.getQueueName()), 1);
      Optional.ofNullable(quotasByTask.remove(task.getTaskId()))
          .ifPresent(quotas -> quotas.forEach(q -> q.onStop(task)));
    }
  }

  private Optional<Project.NameKey> estimateProject(WorkQueue.Task<?> task) {
    Matcher matcher = PROJECT_PATTERN.matcher(task.toString());

    return matcher.find() ? Optional.of(Project.NameKey.parse(matcher.group(1))) : Optional.empty();
  }
}
