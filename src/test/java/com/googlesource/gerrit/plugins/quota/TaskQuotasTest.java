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

import static com.googlesource.gerrit.plugins.quota.QueueStats.Queue.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import com.google.gerrit.server.git.WorkQueue.Task;
import java.util.Random;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TaskQuotasTest {
  private static final String PROJECT_X = "project-x";
  private static final String USER_A = "USER_A";

  @Test
  public void testMaxStartForTaskForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            2,
            2,
"""
[quota "%s"]
  maxStartForTaskForQueue = 1 uploadpack %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    Task<?> u_x_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_1));
    startAndCompleteTask(taskQuotas, u_x_1);

    Task<?> r_x_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_x_1));
    startAndCompleteTask(taskQuotas, r_x_1);

    Task<?> u_x_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_2));

    Task<?> r_x_2 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_x_2));
    startAndCompleteTask(taskQuotas, r_x_2);

    Task<?> u_x_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_x_3));

    Task<?> r_x_3 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_x_3));
    startAndCompleteTask(taskQuotas, r_x_3);

    startAndCompleteTask(taskQuotas, u_x_2);

    assertTrue(taskQuotas.isReadyToStart(u_x_3));
    startAndCompleteTask(taskQuotas, u_x_3);
  }

  private Task<?> task(String queueName, String taskString) {
    Task<?> task = Mockito.mock(Task.class);
    when(task.getTaskId()).thenReturn(new Random().nextInt());
    when(task.getQueueName()).thenReturn(queueName);
    when(task.toString()).thenReturn(taskString);
    return task;
  }

  private TaskQuotas taskQuotas(int interactiveThreads, int batchThreads, String cfg)
      throws ConfigInvalidException {
    Config quotaConfig = new Config();
    quotaConfig.fromText(cfg);
    QuotaFinder finder = spy(new QuotaFinder(null));
    doReturn(quotaConfig).when(finder).getQuotaConfig();
    return new TaskQuotas(finder, interactiveThreads, batchThreads);
  }

  private String uploadPackTask(String project, String user) {
    return "git-upload-pack %s (%s)".formatted(project, user);
  }

  private String receivePackTask(String project, String user) {
    return "git-receive-pack %s (%s)".formatted(project, user);
  }

  private void startAndCompleteTask(TaskQuotas quotas, Task<?> task) {
    quotas.onStart(task);
    quotas.onStop(task);
  }
}
