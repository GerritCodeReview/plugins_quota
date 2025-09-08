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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

  public static Map<Queue, AtomicInteger> availableThreadsPerQueue = new HashMap<>();

  public static void initQueueWithCapacity(Queue q, int c) {
    availableThreadsPerQueue.put(q, new AtomicInteger(c));
  }

  public static boolean acquire(Queue q, int c) {
    AtomicInteger available = availableThreadsPerQueue.get(q);
    if (available == null) {
      return true;
    }

    AtomicBoolean success = new AtomicBoolean(false);
    available.updateAndGet(
        current -> {
          if (current < c) {
            success.setPlain(false);
            return current;
          } else {
            success.setPlain(true);
            return current - c;
          }
        });
    return success.getPlain();
  }

  public static void release(Queue q, int c) {
    AtomicInteger available = availableThreadsPerQueue.get(q);
    if (available != null) {
      available.addAndGet(c);
    }
  }
}
