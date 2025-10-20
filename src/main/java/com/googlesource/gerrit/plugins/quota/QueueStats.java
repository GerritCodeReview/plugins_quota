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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class QueueStats {
  public static final Logger log = LoggerFactory.getLogger(QueueStats.class);

  public record Reservation(int reservedCapacity, Predicate<WorkQueue.Task<?>> taskMatcher) {
    public boolean matches(WorkQueue.Task<?> task) {
      return taskMatcher.test(task);
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

  public static Map<Integer, WorkQueue.Task<?>> tasks = new ConcurrentHashMap<>();
  public static Map<Queue, Integer> spareThreadsPerQueue = new ConcurrentHashMap<>();
  public static Map<Queue, Integer> maxThreadsPerQueue = new ConcurrentHashMap<>();
  public static Map<Queue, Set<Integer>> runningTasksByQueue = new ConcurrentHashMap<>();
  public static Map<Queue, List<Reservation>> reservationsByQueue = new ConcurrentHashMap<>();

  public static void initQueueWithCapacity(Queue q, int c) {
    spareThreadsPerQueue.put(q, c);
    maxThreadsPerQueue.put(q, c);
    runningTasksByQueue.put(q, new HashSet<>());
    reservationsByQueue.put(q, new ArrayList<>());
  }

  public static void registerReservation(String qName, Reservation reservation) {
    Queue q = Queue.fromKey(qName);
    if (!maxThreadsPerQueue.containsKey(q)) {
      return;
    }

    int currentSpareCapacity = spareThreadsPerQueue.get(q);
    int requestedReservationCapacity = reservation.reservedCapacity();
    int actualReservedCapacity =
        Math.max(0, Math.min(requestedReservationCapacity, currentSpareCapacity - 1));

    if (actualReservedCapacity == 0) {
      log.warn(
          "WARNING: Cannot enforce reservation for queue '{}'. "
              + "Requested: {}, Current spare capacity: {}. No threads reserved.",
          qName,
          requestedReservationCapacity,
          currentSpareCapacity);
      return;
    }

    if (actualReservedCapacity < requestedReservationCapacity) {
      log.warn(
          "WARNING: Partial reservation for queue '{}'. "
              + "Requested: {}, Actual reserved: {} (spare capacity: {})",
          qName,
          requestedReservationCapacity,
          actualReservedCapacity,
          currentSpareCapacity);
    }

    Reservation actualReservation =
        new Reservation(actualReservedCapacity, reservation.taskMatcher);
    reservationsByQueue.computeIfAbsent(q, k -> new ArrayList<>()).add(actualReservation);
    spareThreadsPerQueue.compute(q, (key, currentMax) -> currentMax - actualReservedCapacity);
  }

  public static boolean acquire(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    if (!spareThreadsPerQueue.containsKey(q)) {
      return true;
    }
    final int taskId = task.getTaskId();
    final boolean[] acquired = {false};

    tasks.put(taskId, task);
    runningTasksByQueue.computeIfPresent(
        q,
        (queue, running) -> {
          running.add(taskId);

          int maxCapacity = maxThreadsPerQueue.get(q);
          int spareCapacity = spareThreadsPerQueue.get(q);
          List<Reservation> reservations = reservationsByQueue.getOrDefault(q, new ArrayList<>());

          if (running.size() > maxCapacity) {
            running.remove(taskId);
            return running;
          }

          Map<Reservation, Integer> reservedAllocations = new ConcurrentHashMap<>();
          for (Reservation reservation : reservations) {
            reservedAllocations.put(reservation, 0);
          }

          int normalAllocations = 0;
          for (Integer runningTaskId : running) {
            WorkQueue.Task<?> runningTask = tasks.get(runningTaskId);
            if (runningTask == null) {
              continue;
            }

            boolean allocatedToReservation = false;
            for (Reservation reservation : reservations) {
              if (reservation.matches(runningTask)) {
                int currentAllocation = reservedAllocations.get(reservation);
                if (currentAllocation < reservation.reservedCapacity()) {
                  reservedAllocations.put(reservation, currentAllocation + 1);
                  allocatedToReservation = true;
                  break;
                }
              }
            }

            if (!allocatedToReservation) {
              normalAllocations++;
            }
          }

          if (normalAllocations <= spareCapacity) {
            acquired[0] = true;
          } else {
            running.remove(taskId);
          }
          return running;
        });

    if (!acquired[0]) {
      tasks.remove(taskId);
    }
    return acquired[0];
  }

  public static void release(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    if (!runningTasksByQueue.containsKey(q)) {
      return;
    }

    final int taskId = task.getTaskId();
    runningTasksByQueue.computeIfPresent(
        q,
        (queue, runningSet) -> {
          runningSet.remove(taskId);
          return runningSet;
        });

    tasks.remove(taskId);
  }

  public static boolean ensureIdle(Queue q, int c) {
    if (!spareThreadsPerQueue.containsKey(q)) {
      return true;
    }

    return spareThreadsPerQueue.get(q) - runningTasksByQueue.get(q).size() >= c;
  }
}
