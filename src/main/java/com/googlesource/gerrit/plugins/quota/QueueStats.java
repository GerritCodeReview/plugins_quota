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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QueueStats {
  public enum Queue {
    INTERACTIVE("SSH-Interactive-Worker"),
    BATCH("SSH-Batch-Worker"),
    UNKNOWN("UNKNOWN");

    private final String name;

    Queue(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public static String[] keys() {
      return Arrays.stream(Queue.values()).map(Queue::getName).toArray(String[]::new);
    }

    public static Queue fromKey(String key) {
      for (Queue q : Queue.values()) {
        if (q.getName().equals(key)) {
          return q;
        }
      }
      return UNKNOWN;
    }
  }

  public static Map<Queue, Integer> maxThreadsPerQueue = new ConcurrentHashMap<>();
  public static Map<Queue, Set<Integer>> runningTasksPerQueue = new ConcurrentHashMap<>();

  public static void initQueueWithCapacity(Queue q, int c) {
    maxThreadsPerQueue.put(q, c);
    runningTasksPerQueue.put(q, new HashSet<>());
  }

  public static boolean acquire(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    if (!maxThreadsPerQueue.containsKey(q)) {
      return true;
    }
    final int taskId = task.getTaskId();
    final boolean[] acquired = {false};

    runningTasksPerQueue.computeIfPresent(
        q,
        (queue, running) -> {
          if (running.size() < maxThreadsPerQueue.get(q)) {
            running.add(taskId);
            acquired[0] = true;
          }
          return running;
        });

    return acquired[0];
  }

  public static void release(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    if (!runningTasksPerQueue.containsKey(q)) {
      return;
    }

    runningTasksPerQueue.computeIfPresent(
        q,
        (queue, runningSet) -> {
          runningSet.remove(task.getTaskId());
          return runningSet;
        });
  }

  public static boolean ensureIdle(Queue q, int c) {
    if (!maxThreadsPerQueue.containsKey(q)) {
      return true;
    }

    return maxThreadsPerQueue.get(q) - runningTasksPerQueue.get(q).size() >= c;
  }
}
