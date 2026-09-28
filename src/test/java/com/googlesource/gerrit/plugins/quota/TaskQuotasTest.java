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

import static com.googlesource.gerrit.plugins.quota.QueueManager.Queue.BATCH;
import static com.googlesource.gerrit.plugins.quota.QueueManager.Queue.INTERACTIVE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;
import java.util.Random;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TaskQuotasTest {
  private static final String PROJECT_X = "project-x";
  private static final String USER_A = "U.S.E.R-A";
  private static final String USER_B = "USER_B";
  @Mock ProjectCache projectCache;
  @Mock ProjectState projectState;

  @Before
  public void resetParkedQuotaTransitionLoggerState() {
    ParkedQuotaTransitionLogger.parkedSince.clear();
    ParkedQuotaTransitionLogger.prevParkingQuotaByTaskId.clear();
  }

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
  public void testSoftMaxStartForTaskForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            4,
            4,
"""
[quota "%s"]
  softMaxStartForTaskForQueue = 1 uploadpack %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    Task<?> u_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_1));
    taskQuotas.onStart(u_1);

    Task<?> u_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue(
        "Over-cap start is allowed while idle capacity remains", taskQuotas.isReadyToStart(u_2));
    taskQuotas.onStart(u_2);

    Task<?> u_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(
        "Over-cap start is allowed when one idle thread would remain",
        taskQuotas.isReadyToStart(u_3));
    taskQuotas.onStart(u_3);

    Task<?> u_4 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertFalse(
        "Over-cap start must leave at least one idle thread", taskQuotas.isReadyToStart(u_4));

    taskQuotas.onStop(u_1);
    assertTrue(taskQuotas.isReadyToStart(u_4));

    taskQuotas.onStop(u_2);
    taskQuotas.onStop(u_3);
  }

  @Test
  public void testSoftMaxStartForTaskForUserForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            4,
            4,
"""
[quota "%s"]
  softMaxStartForTaskForUserForQueue = 1 uploadpack %s %s
"""
                .formatted(PROJECT_X, USER_A, INTERACTIVE.getName()));

    // running interactive: 1 of 4; user_a uploadpack is at the softMax
    Task<?> u_a_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_a_1));
    taskQuotas.onStart(u_a_1);

    // receivepack is a different task group and is not limited by this soft max
    Task<?> r_a_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_a_1));
    taskQuotas.onStart(r_a_1);

    // running interactive: 3 of 4
    Task<?> u_b_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue("Soft max applies only to the configured user", taskQuotas.isReadyToStart(u_b_1));
    taskQuotas.onStart(u_b_1);

    Task<?> u_a_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(
        "Over-cap start must leave at least one idle thread", taskQuotas.isReadyToStart(u_a_2));

    Task<?> u_a_batch = task(BATCH.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(
        "Soft max applies only to the configured queue", taskQuotas.isReadyToStart(u_a_batch));
    startAndCompleteTask(taskQuotas, u_a_batch);

    // free a thread so idle capacity remains; user_a may then exceed the softMax again
    taskQuotas.onStop(u_b_1);
    assertTrue(taskQuotas.isReadyToStart(u_a_2));
    taskQuotas.onStart(u_a_2);

    // running interactive: 3 of 4; user_a uploadpack is already over the softMax
    Task<?> u_a_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_a_3));

    taskQuotas.onStop(u_a_1);
    assertTrue(taskQuotas.isReadyToStart(u_a_3));

    taskQuotas.onStop(u_a_2);
    taskQuotas.onStop(u_a_3);
    taskQuotas.onStop(r_a_1);
  }

  @Test
  public void testSoftMaxStartForTaskForUserForQueue_cancelledTaskDoesNotFreeSlot()
      throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            3,
            3,
"""
[quota "%s"]
  softMaxStartForTaskForUserForQueue = 1 uploadpack %s %s
"""
                .formatted(PROJECT_X, USER_A, INTERACTIVE.getName()));

    Task<?> u_a_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_a_1));
    taskQuotas.onStart(u_a_1);

    // Occupy another slot with a non-matching task so over-cap uploadpack has no idle thread
    Task<?> r_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(r_1));
    taskQuotas.onStart(r_1);

    Task<?> u_a_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_a_2));

    // onStop for a denied (cancelled) task must not free user_a's occupied slot
    taskQuotas.onStop(u_a_2);
    assertFalse(taskQuotas.isReadyToStart(u_a_2));

    taskQuotas.onStop(u_a_1);
    taskQuotas.onStop(r_1);
  }

  @Test
  public void testSoftMaxStartForTaskForUserForQueueWithRegexTaskGroup()
      throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            3,
            3,
"""
[global]
  softMaxStartForTaskForUserForQueue = 1 ^gerrit[ ]+query.*$ %s %s
"""
                .formatted(USER_A, INTERACTIVE.getName()));

    Task<?> q_a_1 = task(INTERACTIVE.getName(), "gerrit query status:open (%s)".formatted(USER_A));
    assertTrue(taskQuotas.isReadyToStart(q_a_1));
    taskQuotas.onStart(q_a_1);

    Task<?> other = task(INTERACTIVE.getName(), "gerrit ls-projects (%s)".formatted(USER_A));
    assertTrue(
        "Unrelated task should not be limited by the regex", taskQuotas.isReadyToStart(other));
    taskQuotas.onStart(other);

    Task<?> q_a_2 =
        task(INTERACTIVE.getName(), "gerrit query status:merged (%s)".formatted(USER_A));
    assertFalse(
        "Over-cap start must leave at least one idle thread", taskQuotas.isReadyToStart(q_a_2));

    Task<?> q_b_1 = task(INTERACTIVE.getName(), "gerrit query status:open (%s)".formatted(USER_B));
    assertTrue("Soft max applies only to the configured user", taskQuotas.isReadyToStart(q_b_1));
    startAndCompleteTask(taskQuotas, q_b_1);

    taskQuotas.onStop(other);
    assertTrue(taskQuotas.isReadyToStart(q_a_2));

    taskQuotas.onStop(q_a_1);
    taskQuotas.onStop(q_a_2);
  }

  @Test
  public void testSoftMaxStartPerUserForTaskForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
"""
[quota "%s"]
  softMaxStartPerUserForTaskForQueue = 2 uploadpack %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    // running uploadpack: user_a: 1
    Task<?> u_a_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_a_1));
    taskQuotas.onStart(u_a_1);

    // running uploadpack: user_a: 2 (at softMax)
    Task<?> u_a_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_a_2));
    taskQuotas.onStart(u_a_2);

    // running uploadpack: user_a: 3 (may exceed softMax while idle remains)
    Task<?> u_a_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_a_3));
    taskQuotas.onStart(u_a_3);

    // running uploadpack: user_a: 3 user_b: 1
    Task<?> u_b_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(u_b_1));
    taskQuotas.onStart(u_b_1);

    // receivepack is a different task group and is not limited by this soft max
    Task<?> r_a_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(r_a_1));
    taskQuotas.onStart(r_a_1);

    // running: uploadpack a:2 b:1, receivepack a:1 → 4 of 5 used; user_a at softMax
    taskQuotas.onStop(u_a_1);
    Task<?> u_a_4 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(
        "Over-cap start must leave at least one idle thread", taskQuotas.isReadyToStart(u_a_4));

    // free a thread so idle capacity remains; user_a may then exceed softMax again
    taskQuotas.onStop(u_b_1);
    assertTrue(taskQuotas.isReadyToStart(u_a_4));
    taskQuotas.onStart(u_a_4);

    taskQuotas.onStop(u_a_2);
    taskQuotas.onStop(u_a_3);
    taskQuotas.onStop(u_a_4);
    taskQuotas.onStop(r_a_1);
  }

  @Test
  public void testSoftMaxStartPerUserForTaskForQueue_cancelledTaskDoesNotFreeSlot()
      throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            3,
            3,
"""
[quota "%s"]
  softMaxStartPerUserForTaskForQueue = 1 uploadpack %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    Task<?> u_a_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_a_1));
    taskQuotas.onStart(u_a_1);

    // Occupy another slot with a non-matching task so over-cap uploadpack has no idle thread
    Task<?> r_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(r_1));
    taskQuotas.onStart(r_1);

    Task<?> u_a_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_a_2));

    // onStop for a denied (cancelled) task must not free user_a's occupied slot
    taskQuotas.onStop(u_a_2);
    assertFalse(taskQuotas.isReadyToStart(u_a_2));

    taskQuotas.onStop(u_a_1);
    taskQuotas.onStop(r_1);
  }

  @Test
  public void testHttpGitTaskMatchesProjectQuotaWhenPrefixedProjectAbsent()
      throws ConfigInvalidException {
    when(projectCache.get(Project.NameKey.parse("a/" + PROJECT_X))).thenReturn(Optional.empty());
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
    taskQuotas.onStart(u_x_1);

    Task<?> u_x_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A, true));
    assertFalse(taskQuotas.isReadyToStart(u_x_2));

    taskQuotas.onStop(u_x_1);
    assertTrue(taskQuotas.isReadyToStart(u_x_2));
    startAndCompleteTask(taskQuotas, u_x_2);
  }

  @Test
  public void testHttpGitTaskBypassesProjectQuotaWhenPrefixedProjectPresent()
      throws ConfigInvalidException {
    when(projectCache.get(Project.NameKey.parse("a/" + PROJECT_X)))
        .thenReturn(Optional.of(projectState));
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
    taskQuotas.onStart(u_x_1);

    // project-x is at its limit, but "a/project-x" is a distinct project — not limited
    Task<?> u_ax_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A, true));
    assertTrue(taskQuotas.isReadyToStart(u_ax_1));
    startAndCompleteTask(taskQuotas, u_ax_1);

    taskQuotas.onStop(u_x_1);
  }

  @Test
  public void testPathPrefixesShareSameProjectQuota() throws ConfigInvalidException {
    when(projectCache.get(Project.NameKey.parse("a/" + PROJECT_X))).thenReturn(Optional.empty());
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
"""
[quota "%s"]
  maxStartForTaskForQueue = 1 uploadpack %s
"""
                .formatted(PROJECT_X, INTERACTIVE.getName()));

    Task<?> bare = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(bare));
    taskQuotas.onStart(bare);

    for (String path : new String[] {"/" + PROJECT_X, "/./" + PROJECT_X, "a/" + PROJECT_X}) {
      Task<?> variant = task(INTERACTIVE.getName(), uploadPackTask(path, USER_A));
      assertFalse(
          "path [" + path + "] should share the " + PROJECT_X + " quota",
          taskQuotas.isReadyToStart(variant));
    }

    taskQuotas.onStop(bare);
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

    Task<?> u_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    Task<?> u_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));

    assertTrue(taskQuotas.isReadyToStart(u_1));
    taskQuotas.onStart(u_1);
    assertTrue(taskQuotas.isReadyToStart(u_2));
    taskQuotas.onStart(u_2);

    Task<?> u_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(
        "General task should be blocked; remaining slots are reserved for receivepack",
        taskQuotas.isReadyToStart(u_3));

    Task<?> r_1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertTrue(
        "Reserved task type should be allowed to use reserved slots",
        taskQuotas.isReadyToStart(r_1));
    taskQuotas.onStart(r_1);

    Task<?> r_2 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(r_2));
    taskQuotas.onStart(r_2);

    Task<?> r_3 = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_A));
    assertFalse(
        "Absolute ceiling (maxStart) should still block even reserved tasks",
        taskQuotas.isReadyToStart(r_3));
  }

  @Test
  public void testPoolSharedAcrossQueues() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
            """
            [pool "uploads"]
              max = 2
            [global]
              maxStartForTaskForQueue = pool:uploads uploadpack %s
              maxStartForTaskForQueue = pool:uploads uploadpack %s
            """
                .formatted(INTERACTIVE.getName(), BATCH.getName()));

    Task<?> interactive1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(interactive1));
    taskQuotas.onStart(interactive1);

    Task<?> batch1 = task(BATCH.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(batch1));
    taskQuotas.onStart(batch1);

    Task<?> interactive2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertFalse(
        "pool capacity is shared across both queues and is now exhausted",
        taskQuotas.isReadyToStart(interactive2));

    taskQuotas.onStop(batch1);
    assertTrue(
        "stopping the batch task frees capacity for the interactive queue",
        taskQuotas.isReadyToStart(interactive2));
    startAndCompleteTask(taskQuotas, interactive2);

    taskQuotas.onStop(interactive1);
  }

  @Test
  public void testPoolTokenSharedAcrossProjects() throws ConfigInvalidException {
    String projectY = "project-y";
    TaskQuotas taskQuotas =
        taskQuotas(
            2,
            2,
            """
            [pool "uploads"]
              max = 1
            [quota "%s"]
              maxStartForTaskForQueue = pool:uploads uploadpack %s
            [quota "%s"]
              maxStartForTaskForQueue = pool:uploads uploadpack %s
            """
                .formatted(PROJECT_X, INTERACTIVE.getName(), projectY, INTERACTIVE.getName()));

    Task<?> x1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(x1));
    taskQuotas.onStart(x1);

    Task<?> y1 = task(INTERACTIVE.getName(), uploadPackTask(projectY, USER_A));
    assertFalse(
        "pool capacity is shared across projects and is now exhausted",
        taskQuotas.isReadyToStart(y1));

    taskQuotas.onStop(x1);
  }

  @Test
  public void testPoolSharedAcrossDifferentTaskGroups() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
            """
            [pool "gitops"]
              max = 1
            [global]
              maxStartForTaskForQueue = pool:gitops uploadpack %s
              maxStartForTaskForQueue = pool:gitops receivepack %s
            """
                .formatted(INTERACTIVE.getName(), INTERACTIVE.getName()));

    Task<?> upload = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(upload));
    taskQuotas.onStart(upload);

    Task<?> receive = task(INTERACTIVE.getName(), receivePackTask(PROJECT_X, USER_B));
    assertFalse(
        "uploadpack and receivepack lines referencing the same pool share one counter",
        taskQuotas.isReadyToStart(receive));

    taskQuotas.onStop(upload);
    startAndCompleteTask(taskQuotas, receive);
  }

  @Test
  public void testPoolSharedAcrossQueuesForPerUserForTaskForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
            """
            [pool "uploads"]
              maxPerUser = 2
            [global]
              maxStartPerUserForTaskForQueue = pool:uploads uploadpack %s
              maxStartPerUserForTaskForQueue = pool:uploads uploadpack %s
            """
                .formatted(INTERACTIVE.getName(), BATCH.getName()));

    Task<?> interactive1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(interactive1));
    taskQuotas.onStart(interactive1);

    Task<?> batch1 = task(BATCH.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(batch1));
    taskQuotas.onStart(batch1);

    Task<?> interactive2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(
        "user_a's per-user cap is shared across both pooled queues and is now exhausted",
        taskQuotas.isReadyToStart(interactive2));

    Task<?> otherUserInteractive = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue(
        "the per-user cap is tracked separately for each user",
        taskQuotas.isReadyToStart(otherUserInteractive));
    startAndCompleteTask(taskQuotas, otherUserInteractive);

    taskQuotas.onStop(batch1);
    assertTrue(
        "stopping user_a's batch task frees pooled capacity for the interactive queue",
        taskQuotas.isReadyToStart(interactive2));
    startAndCompleteTask(taskQuotas, interactive2);

    taskQuotas.onStop(interactive1);
  }

  @Test
  public void testPoolWithMaxAndMaxPerUserUsedByDifferentKeywords() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
            """
            [pool "uploads"]
              max = 3
              maxPerUser = 1
            [global]
              maxStartForTaskForQueue = pool:uploads uploadpack %s
              maxStartPerUserForTaskForQueue = pool:uploads uploadpack %s
            """
                .formatted(INTERACTIVE.getName(), BATCH.getName()));

    Task<?> a1 = task(BATCH.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(a1));
    taskQuotas.onStart(a1);

    Task<?> a2 = task(BATCH.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(
        "maxPerUser = 1 blocks a second task for the same user", taskQuotas.isReadyToStart(a2));

    Task<?> b1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue(taskQuotas.isReadyToStart(b1));
    taskQuotas.onStart(b1);

    Task<?> b2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertTrue("max = 3 is a separate counter from maxPerUser", taskQuotas.isReadyToStart(b2));
    taskQuotas.onStart(b2);

    Task<?> b3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertFalse("max = 3 counter is exhausted", taskQuotas.isReadyToStart(b3));
  }

  @Test
  public void testPoolReferencedForUndefinedLimitIsSkipped() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
            """
            [pool "uploads"]
              maxPerUser = 1
            [global]
              maxStartForTaskForQueue = pool:uploads uploadpack %s
            """
                .formatted(INTERACTIVE.getName()));

    Task<?> t1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    Task<?> t2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(t1));
    taskQuotas.onStart(t1);
    assertTrue(
        "quota referencing an undefined limit is not enforced", taskQuotas.isReadyToStart(t2));
  }

  @Test
  public void testPoolUnsupportedKeysAreIgnored() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            5,
            5,
            """
            [pool "uploads"]
              max = 1
              min = 5
              maxPerProject = 1
            [global]
              maxStartForTaskForQueue = pool:uploads uploadpack %s
            """
                .formatted(INTERACTIVE.getName()));

    Task<?> t1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(t1));
    taskQuotas.onStart(t1);
    Task<?> t2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_B));
    assertFalse("max is still enforced", taskQuotas.isReadyToStart(t2));
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
    QuotaFinder finder = spy(new QuotaFinder(null, null));
    doReturn(quotaConfig).when(finder).getQuotaConfig();
    ProjectResolver projectResolver = new ProjectResolver(projectCache);
    return new TaskQuotas(
        finder,
        projectResolver,
        new TaskQuotaKeys(
            new MinStartForQueueQuota(projectResolver),
            new MinStartForTaskForQueueQuota(projectResolver)),
        interactiveThreads,
        batchThreads);
  }

  private String uploadPackTask(String project, String user) {
    return uploadPackTask(project, user, false);
  }

  private String uploadPackTask(String project, String user, boolean isHttp) {
    return "git-upload-pack %s%s (%s)".formatted(isHttp ? "a/" : "", project, user);
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

    Task<?> queryTask = task(INTERACTIVE.getName(), "gerrit query status:open (user-a)");
    assertTrue("First query should be allowed", taskQuotas.isReadyToStart(queryTask));
    taskQuotas.onStart(queryTask);

    Task<?> secondQuery = task(INTERACTIVE.getName(), "gerrit query status:merged (user-a)");
    assertFalse(
        "Second query should be blocked by regex quota", taskQuotas.isReadyToStart(secondQuery));

    Task<?> otherTask = task(INTERACTIVE.getName(), "gerrit ls-projects (user-a)");
    assertTrue(
        "Unrelated task should not be limited by the regex", taskQuotas.isReadyToStart(otherTask));
    startAndCompleteTask(taskQuotas, otherTask);

    taskQuotas.onStop(queryTask);
    assertTrue(
        "Second query should be allowed after first one stops",
        taskQuotas.isReadyToStart(secondQuery));
    startAndCompleteTask(taskQuotas, secondQuery);
  }

  @Test
  public void testMaxParkedInterruptsInsteadOfParking() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            1,
            1,
"""
[global]
  maxStartForTaskForQueue = 1 uploadpack %s
  maxParked = 1
"""
                .formatted(INTERACTIVE.getName()));

    Task<?> u_1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_1));
    taskQuotas.onStart(u_1);

    // No tasks parked yet, so u_2 is parked normally rather than interrupted.
    Task<?> u_2 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertFalse(taskQuotas.isReadyToStart(u_2));
    verify(u_2, Mockito.never()).cancel(true);

    // maxParked (1) is already reached (u_2 is parked), so u_3 is interrupted instead.
    Task<?> u_3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT_X, USER_A));
    assertTrue(taskQuotas.isReadyToStart(u_3));
    verify(u_3).cancel(true);

    taskQuotas.onStop(u_2);
  }
}
