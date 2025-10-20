// Copyright (C) 2014 The Android Open Source Project
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

import com.googlesource.gerrit.plugins.quota.QueueStats.Queue;
import com.google.gerrit.server.git.WorkQueue;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QueueStatsTest {
  private static final Queue TEST_QUEUE = Queue.INTERACTIVE;
  private static final String TEST_QUEUE_NAME = TEST_QUEUE.getName();
  private static final int MAX_CAPACITY = 3;

  @Before
  public void setUp() {
    QueueStats.maxThreadsPerQueue.clear();
    QueueStats.runningTasksPerQueue.clear();
  }

  @Test
  public void testInitQueueWithCapacity_InitializesMaps() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, MAX_CAPACITY);

    assertTrue(
        "maxThreadsPerQueue should contain the queue.",
        QueueStats.maxThreadsPerQueue.containsKey(TEST_QUEUE));
    assertEquals(
        "maxThreadsPerQueue should store the correct capacity.",
        MAX_CAPACITY,
        (int) QueueStats.maxThreadsPerQueue.get(TEST_QUEUE));
    assertTrue(
        "runningTasksPerQueue should contain the queue.",
        QueueStats.runningTasksPerQueue.containsKey(TEST_QUEUE));
    assertTrue(
        "runningTasksPerQueue set should be empty initially.",
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).isEmpty());
  }

  @Test
  public void testAcquire_UnmanagedQueue_Succeeds() {
    WorkQueue.Task<?> task = createTask(1, "UNMANAGED-QUEUE");

    assertTrue("Acquire should succeed for an unmanaged queue.", QueueStats.acquire(task));
    assertTrue("Running tasks map should remain empty.", QueueStats.runningTasksPerQueue.isEmpty());
  }

  @Test
  public void testAcquire_WithinCapacity_Succeeds() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, MAX_CAPACITY);
    WorkQueue.Task<?> task1 = createTask(101, TEST_QUEUE_NAME);
    WorkQueue.Task<?> task2 = createTask(102, TEST_QUEUE_NAME);

    assertTrue("First acquire should succeed.", QueueStats.acquire(task1));
    assertTrue("Second acquire should succeed.", QueueStats.acquire(task2));

    assertEquals(
        "Running tasks count should be 2.",
        2,
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).size());
    assertTrue(
        "Task 1 ID should be registered.",
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).contains(101));
  }

  @Test
  public void testAcquire_ExceedsCapacity_Fails() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, 1); // Capacity of 1
    WorkQueue.Task<?> task1 = createTask(101, TEST_QUEUE_NAME);
    WorkQueue.Task<?> task2 = createTask(102, TEST_QUEUE_NAME);

    assertTrue("First acquire (within capacity) should succeed.", QueueStats.acquire(task1));
    assertFalse("Second acquire (exceeding capacity) should fail.", QueueStats.acquire(task2));

    assertEquals(
        "Running tasks count should remain 1.",
        1,
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).size());
    assertFalse(
        "Task 2 ID should not be registered.",
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).contains(102));
  }

  @Test
  public void testRelease_RemovesTask() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, MAX_CAPACITY);
    WorkQueue.Task<?> task = createTask(201, TEST_QUEUE_NAME);

    QueueStats.acquire(task);
    assertTrue(
        "Task should be running before release.",
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).contains(201));

    QueueStats.release(task);
    assertFalse(
        "Task should be removed after release.",
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).contains(201));
    assertTrue(
        "Running tasks set should be empty.",
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).isEmpty());
  }

  @Test
  public void testRelease_UnregisteredQueue_DoesNothing() {
    WorkQueue.Task<?> task = createTask(201, TEST_QUEUE_NAME);

    QueueStats.release(task);
    assertTrue(QueueStats.runningTasksPerQueue.isEmpty());
  }

  @Test
  public void testRelease_NonRunningTask_DoesNothing() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, MAX_CAPACITY);
    WorkQueue.Task<?> runningTask = createTask(301, TEST_QUEUE_NAME);
    WorkQueue.Task<?> releasedTask = createTask(302, TEST_QUEUE_NAME);

    QueueStats.acquire(runningTask);
    int initialSize = QueueStats.runningTasksPerQueue.get(TEST_QUEUE).size();

    QueueStats.release(releasedTask);
    assertEquals(
        "Running tasks count should not change.",
        initialSize,
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).size());
  }

  // --- Test ensureIdle ---

  @Test
  public void testEnsureIdle_UnmanagedQueue_IsIdle() {
    assertTrue("Unmanaged queue should always be idle.", QueueStats.ensureIdle(Queue.BATCH, 0));
  }

  @Test
  public void testEnsureIdle_FullCapacity_NotIdle() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, 2);
    WorkQueue.Task<?> task1 = createTask(401, TEST_QUEUE_NAME);
    WorkQueue.Task<?> task2 = createTask(402, TEST_QUEUE_NAME);
    QueueStats.acquire(task1);
    QueueStats.acquire(task2); // 2 running, 0 available

    assertFalse(
        "Should not be idle when available threads (0) < threshold (1).",
        QueueStats.ensureIdle(TEST_QUEUE, 1));
    assertTrue(
        "Should be idle when available threads (0) >= threshold (0).",
        QueueStats.ensureIdle(TEST_QUEUE, 0));
  }

  @Test
  public void testEnsureIdle_PartialCapacity_IsIdle() {
    QueueStats.initQueueWithCapacity(TEST_QUEUE, 5);
    WorkQueue.Task<?> task1 = createTask(501, TEST_QUEUE_NAME);
    QueueStats.acquire(task1); // 1 running, 4 available

    assertTrue(
        "Should be idle when available threads (4) >= threshold (3).",
        QueueStats.ensureIdle(TEST_QUEUE, 3));

    assertFalse(
        "Should not be idle when available threads (4) < threshold (5).",
        QueueStats.ensureIdle(TEST_QUEUE, 5));
  }

  @Test
  public void testAcquire_Concurrency_RespectsCapacity() throws InterruptedException {
    int poolSize = 10;
    int numTasks = 1000;

    QueueStats.initQueueWithCapacity(TEST_QUEUE, MAX_CAPACITY);
    ExecutorService executor = Executors.newFixedThreadPool(poolSize);
    CountDownLatch latch = new CountDownLatch(numTasks);

    Set<Integer> acquired = new HashSet<>();

    IntStream.range(0, numTasks)
        .forEach(
            i -> {
              executor.submit(
                  () -> {
                    WorkQueue.Task<?> task = createTask(i, TEST_QUEUE_NAME);
                    if (QueueStats.acquire(task)) {
                      acquired.add(task.getTaskId());
                    }
                    latch.countDown();
                  });
            });

    assertTrue("All tasks should attempt to acquire.", latch.await(5, TimeUnit.SECONDS));
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.SECONDS);

    assertEquals(
        "The number of running tasks must not exceed the max capacity due to race conditions.",
        MAX_CAPACITY,
        QueueStats.runningTasksPerQueue.get(TEST_QUEUE).size());

    assertEquals(
        "The correct number of unique task IDs should be acquired.", MAX_CAPACITY, acquired.size());
  }

  private WorkQueue.Task<?> createTask(int id, String queueName) {
    WorkQueue.Task<?> task = mock(WorkQueue.Task.class);
    when(task.getQueueName()).thenReturn(queueName);
    when(task.getTaskId()).thenReturn(id);
    return task;
  }
}
