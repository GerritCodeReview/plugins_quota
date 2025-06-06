package com.googlesource.gerrit.plugins.quota;

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

import com.google.common.base.Strings;
import com.google.gerrit.server.git.WorkQueue;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TaskBudgetGenerator {
  private static final Logger log = LoggerFactory.getLogger(TaskBudgetGenerator.class);
  private final TaskBudgetConfig cfg;

  @Inject
  public TaskBudgetGenerator(TaskBudgetConfig cfg) {
    this.cfg = cfg;
  }

  Map<TaskBudget, WorkQueue.TaskParker> generateParkers() {
    return cfg.getBudgets().stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                budget ->
                    new WorkQueue.TaskParker() {
                      final Semaphore semaphore = new Semaphore(budget.startMax());

                      @Override
                      public void onStart(WorkQueue.Task<?> task) {}

                      @Override
                      public void onStop(WorkQueue.Task<?> task) {
                        if (!doesApply(task)) {
                          return;
                        }

                        semaphore.release();
                      }

                      @Override
                      public boolean isReadyToStart(WorkQueue.Task<?> task) {
                        task.toString();
                        if (!doesApply(task)) {
                          return true;
                        }
                        log.warn("semaphore stats: " + semaphore.availablePermits());

                        boolean ready = semaphore.tryAcquire();
                        if (!ready) {
                          log.warn(
                              "Task [{}] will be parked because of rule [{}]", task, budget.rule());
                        }
                        return ready;
                      }

                      @Override
                      public void onNotReadyToStart(WorkQueue.Task<?> task) {
                        if (!doesApply(task)) {
                          return;
                        }

                        semaphore.release();
                      }

                      private boolean doesApply(WorkQueue.Task<?> task) {
                        return (Strings.isNullOrEmpty(budget.task())
                                || task.toString().startsWith(budget.task()))
                            && (budget.queues().isEmpty()
                                || budget.queues().stream()
                                    .anyMatch(q -> q.equals(task.getQueueName())));
                      }
                    }));
  }
}
