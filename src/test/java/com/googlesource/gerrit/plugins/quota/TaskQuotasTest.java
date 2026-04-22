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

import static com.googlesource.gerrit.plugins.quota.QueueManager.Queue.INTERACTIVE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
  private static final String USER_A = "USER-A";
  private static final String USER_B = "USER_B";

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
    taskQuotas.onStart(u_x_2);

    Task<?> r_x_2 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_x_2));
    startAndCompleteTask(taskQuotas, r_x_2);

    Task<?> u_x_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_x_3));

    Task<?> r_x_3 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_x_3));
    startAndCompleteTask(taskQuotas, r_x_3);

    taskQuotas.onStop(u_x_2);

    assertTrue(taskQuotas.isReadyToStart(u_x_3));
    startAndCompleteTask(taskQuotas, u_x_3);
  }

  @Test
  public void testMaxStartForTaskForUserForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            2,
            2,
"""
[quota "%s"]
  maxStartForTaskForUserForQueue = 1 uploadpack %s %s
"""
                .formatted(PROJECT_X, USER_A, INTERACTIVE.getName()));

    Task<?> u_x_a_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_a_1));
    startAndCompleteTask(taskQuotas, u_x_a_1);

    Task<?> u_x_a_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_a_2));
    taskQuotas.onStart(u_x_a_2);

    Task<?> u_x_a_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_x_a_3));

    Task<?> u_x_b_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(u_x_b_1));
    startAndCompleteTask(taskQuotas, u_x_b_1);

    taskQuotas.onStop(u_x_a_2);
    assertTrue(taskQuotas.isReadyToStart(u_x_a_3));
    startAndCompleteTask(taskQuotas, u_x_a_3);
  }

  @Test
  public void testSoftMaxPerUserForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
"""
[quota "%s"]
  softMaxStartPerUserForQueue = 2 %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    // running: user_a: 1 user_b: 0
    Task<?> u_x_a_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_a_1));
    taskQuotas.onStart(u_x_a_1);

    // running: user_a: 2 user_b: 0 (user_a is at the softMax)
    Task<?> u_x_a_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_a_2));
    taskQuotas.onStart(u_x_a_2);

    // running: user_a: 3 user_b: 0 (user_a able to start new task exceeding soft max)
    Task<?> u_x_a_3 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_x_a_3));
    taskQuotas.onStart(u_x_a_3);

    // running: user_a: 3 user_b: 1
    Task<?> u_x_b_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(u_x_b_1));
    taskQuotas.onStart(u_x_b_1);

    // running: user_a: 3 user_b: 2
    Task<?> u_x_b_2 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(u_x_b_2));
    taskQuotas.onStart(u_x_b_2);

    // running: user_a: 2 user_b: 2
    taskQuotas.onStop(u_x_a_1);
    Task<?> u_x_a_4 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_x_a_4));

    // running: user_a: 2 user_b: 1
    taskQuotas.onStop(u_x_b_1);

    // running: user_a: 3 user_b: 1
    assertTrue(taskQuotas.isReadyToStart(u_x_a_4));
    taskQuotas.onStart(u_x_a_4);

    taskQuotas.onStop(u_x_a_2);
    taskQuotas.onStop(u_x_a_3);
    taskQuotas.onStop(u_x_a_4);
    taskQuotas.onStop(u_x_b_2);
  }

  @Test
  public void testMinStartForTaskForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            4,
            4,
"""
[quota "%s"]
  minStartForTaskForQueue = 2 receivepack %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    // 1. Fill the 'General' capacity (4 total - 2 reserved = 2 general slots)
    Task<?> u_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    Task<?> u_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    
    assertTrue(taskQuotas.isReadyToStart(u_1));
    taskQuotas.onStart(u_1);
    assertTrue(taskQuotas.isReadyToStart(u_2));
    taskQuotas.onStart(u_2);

    // 2. Attempt a 3rd 'uploadpack' (General Task)
    // This should be BLOCKED because the last 2 seats are reserved for 'receivepack'
    Task<?> u_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse("General task should be blocked; remaining slots are reserved for receivepack", 
                taskQuotas.isReadyToStart(u_3));

    // 3. Attempt a 'receivepack' (The Reserved Task)
    // This should be ALLOWED because it matches the reservation criteria
    Task<?> r_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue("Reserved task type should be allowed to use reserved slots", 
               taskQuotas.isReadyToStart(r_1));
    taskQuotas.onStart(r_1);

    // 4. Verify the absolute ceiling still works
    // We have 3 tasks running (u_1, u_2, r_1). 1 slot left (reserved for receivepack).
    Task<?> r_2 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(r_2));
    taskQuotas.onStart(r_2);

    // Now 4/4 are used. Even a reserved task should be blocked now.
    Task<?> r_3 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertFalse("Absolute ceiling (maxStart) should still block even reserved tasks", 
                taskQuotas.isReadyToStart(r_3));
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
