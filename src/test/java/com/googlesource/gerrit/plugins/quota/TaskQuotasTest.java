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

  @Test
  public void testMaxStartWithRegexTaskGroup() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            2,
            2,
"""
[global]
  # Limiting gerrit query tasks specifically using a regex
  maxStartForTaskForQueue = 1 ^gerrit[ ]+query.*$ %s
"""
                .formatted(INTERACTIVE.getName()));

    // This task matches the regex ^gerrit[ ]+query.*
    Task<?> queryTask = task(INTERACTIVE.getName(), "gerrit query status:open (user-a)");
    assertTrue("First query should be allowed", taskQuotas.isReadyToStart(queryTask));
    taskQuotas.onStart(queryTask);

    // This second task also matches the regex and should be blocked (limit is 1)
    Task<?> secondQuery = task(INTERACTIVE.getName(), "gerrit query status:merged (user-a)");
    assertFalse("Second query should be blocked by regex quota", taskQuotas.isReadyToStart(secondQuery));

    // This task does NOT match the regex and should be allowed immediately
    Task<?> otherTask = task(INTERACTIVE.getName(), "gerrit ls-projects (user-a)");
    assertTrue("Unrelated task should not be limited by the regex", taskQuotas.isReadyToStart(otherTask));
    startAndCompleteTask(taskQuotas, otherTask);

    // After finishing the first query, the second one should be ready
    taskQuotas.onStop(queryTask);
    assertTrue("Second query should be allowed after first one stops", taskQuotas.isReadyToStart(secondQuery));
    startAndCompleteTask(taskQuotas, secondQuery);
  }
}
