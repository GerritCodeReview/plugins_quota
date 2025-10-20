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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueStats {
  record QueueInfo(int maxThreads, Set<Integer> runningTasks) {
    public QueueInfo(int maxThreads) {
      this(maxThreads, new HashSet<>());
    }
  }

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

  public static Map<Queue, QueueInfo> infoByQueue = new ConcurrentHashMap<>();

  public static void initQueueWithCapacity(Queue q, int c) {
    infoByQueue.put(q, new QueueInfo(c));
  }

  public static boolean acquire(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    if (q == Queue.UNKNOWN) {
      return true;
    }

    final AtomicBoolean acquired = new AtomicBoolean(false);
    infoByQueue.computeIfPresent(
        q,
        (queue, info) -> {
          if (info.runningTasks().size() < info.maxThreads()) {
            info.runningTasks().add(task.getTaskId());
            acquired.setPlain(true);
          }
          return info;
        });

    return acquired.getPlain();
  }

  public static void release(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    infoByQueue.computeIfPresent(
        q,
        (queue, info) -> {
          info.runningTasks().remove(task.getTaskId());
          return info;
        });
  }

  public static boolean ensureIdle(Queue q, int c) {
    QueueInfo info = infoByQueue.get(q);
    if (info == null) {
      return true;
    }

    return info.maxThreads() - info.runningTasks().size() >= c;
  }
}
