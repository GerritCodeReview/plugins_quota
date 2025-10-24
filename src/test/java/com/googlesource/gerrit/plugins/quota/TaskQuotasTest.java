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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import com.google.gerrit.server.git.WorkQueue;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Random;
import static com.googlesource.gerrit.plugins.quota.QueueStats.Queue.*;

@RunWith(MockitoJUnitRunner.class)
public class TaskQuotasTest {
  private static final String PROJECT = "sample-project";
  private static final String USER_A = "USER_A";
  private static final String USER_B = "USER_B";

  @Test
  public void testMaxStartForTaskForQueue() throws ConfigInvalidException {
    TaskQuotas taskQuotas =
        taskQuotas(
            2,
            2,
"""
[quota "sample-project"]
  maxStartForTaskForQueue = 1 uploadpack SSH-Interactive-Worker
""");

    WorkQueue.Task<?> up1 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT, USER_A));
    WorkQueue.Task<?> up2 = task(INTERACTIVE.getName(), uploadPackTask("/" + PROJECT, USER_A));
    WorkQueue.Task<?> up3 = task(INTERACTIVE.getName(), uploadPackTask(PROJECT + ".git", USER_A));

    WorkQueue.Task<?> rp1 = task(INTERACTIVE.getName(), receivePackTask(PROJECT, USER_A));
    WorkQueue.Task<?> rp2 = task(INTERACTIVE.getName(), receivePackTask("/" + PROJECT, USER_A));
    WorkQueue.Task<?> rp3 = task(INTERACTIVE.getName(), receivePackTask(PROJECT + ".git", USER_A));

    assertTrue(taskQuotas.isReadyToStart(up1));
    startAndCompleteTask(taskQuotas, up1);

    assertTrue(taskQuotas.isReadyToStart(rp1));
    startAndCompleteTask(taskQuotas, rp1);

    assertTrue(taskQuotas.isReadyToStart(up2));

    assertTrue(taskQuotas.isReadyToStart(rp2));
    startAndCompleteTask(taskQuotas, rp2);

    assertFalse(taskQuotas.isReadyToStart(up3));

    assertTrue(taskQuotas.isReadyToStart(rp3));
    startAndCompleteTask(taskQuotas, rp3);

    startAndCompleteTask(taskQuotas, up2);

    assertTrue(taskQuotas.isReadyToStart(up3));
    startAndCompleteTask(taskQuotas, up3);
  }

  private WorkQueue.Task<?> task(String queueName, String taskString) {
    WorkQueue.Task<?> task = Mockito.mock(WorkQueue.Task.class);
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

  private void startAndCompleteTask(TaskQuotas quotas, WorkQueue.Task<?> task) {
    quotas.onStart(task);
    quotas.onStop(task);
  }
}
