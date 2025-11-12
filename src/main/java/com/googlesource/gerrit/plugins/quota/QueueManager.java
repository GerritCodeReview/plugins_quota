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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.googlesource.gerrit.plugins.quota.TaskQuotas.quotaLog;

public class QueueManager {
  public static class QueueInfo {
    public final int maxThreads;
    public int spareThreads;
    public final Map<Integer, WorkQueue.Task<?>> runningTaskById;
    public final List<Reservation> reservations;

    public QueueInfo(int maxThreads) {
      this.maxThreads = maxThreads;
      this.spareThreads = maxThreads;
      this.runningTaskById = new HashMap<>();
      this.reservations = new ArrayList<>();
    }

    public boolean run(WorkQueue.Task<?> task) {
      if (runningTaskById.size() >= maxThreads) {
        return false;
      }

      if (runningTaskById.put(task.getTaskId(), task) != null) {
        return true;
      }

      if (!reservations.isEmpty() && !canAllocate()) {
        quotaLog.atInfo().log("Task [%s] will be parked due to reservations", task);
        runningTaskById.remove((task.getTaskId()));
        return false;
      }

      return true;
    }

    public void complete(WorkQueue.Task<?> task) {
      runningTaskById.remove(task.getTaskId());
    }

    public boolean ensureIdle(int threads) {
      return maxThreads - runningTaskById.size() >= threads;
    }

    public void addReservation(Reservation incomingReservation) {
      reservations.add(incomingReservation);
      spareThreads -= incomingReservation.reservedCapacity();
    }

    public boolean canAllocate() {
      int spareAllocations = 0;
      Map<Reservation, Integer> allocationsByReservation = new HashMap<>();

      for (WorkQueue.Task<?> runningTask : runningTaskById.values()) {
        boolean allocatedToReservation = false;
        for (Reservation reservation : reservations) {
          if (reservation.matches(runningTask)) {
            int currentAllocation = allocationsByReservation.getOrDefault(reservation, 0);
            if (currentAllocation < reservation.reservedCapacity()) {
              allocationsByReservation.put(reservation, currentAllocation + 1);
              allocatedToReservation = true;
              break;
            }
          }
        }

        if (!allocatedToReservation) {
          spareAllocations++;
        }
      }

      return spareAllocations <= spareThreads;
    }
  }

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

  public static ConcurrentMap<Queue, QueueInfo> infoByQueue = new ConcurrentHashMap<>();

  public static void initQueueWithCapacity(Queue q, int c) {
    infoByQueue.put(q, new QueueInfo(c));
  }

  public static void registerReservation(String qName, Reservation reservation) {
    Queue q = Queue.fromKey(qName);
    if (q == Queue.UNKNOWN) {
      return;
    }

    QueueInfo queueInfo = infoByQueue.get(q);
    int capacityToReserve = queueInfo.spareThreads - 1;
    if (capacityToReserve < 1) {
      quotaLog.atSevere().log(
          "Cannot enforce reservation for queue '{}' Requested: {} threads. No threads reserved.",
          qName,
          reservation.reservedCapacity());
      return;
    }

    if (reservation.reservedCapacity() > capacityToReserve) {
      quotaLog.atWarning().log(
          "Partial reservation enforced for queue '{}'. Requested: {}, Actual reserved: {}",
          qName,
          reservation.reservedCapacity(),
          capacityToReserve);
      queueInfo.addReservation(new Reservation(capacityToReserve, reservation.taskMatcher));
      return;
    }

    queueInfo.addReservation(reservation);
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
          acquired.setPlain(info.run(task));
          return info;
        });

    return acquired.getPlain();
  }

  public static void release(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    infoByQueue.computeIfPresent(
        q,
        (queue, info) -> {
          info.complete(task);
          return info;
        });
  }

  public static boolean ensureIdle(Queue q, int c) {
    QueueInfo info = infoByQueue.get(q);
    if (info == null) {
      return true;
    }

    return info.ensureIdle(c);
  }
}
