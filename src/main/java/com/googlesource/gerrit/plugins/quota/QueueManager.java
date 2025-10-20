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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueManager {
  public static final Logger log = LoggerFactory.getLogger(QueueManager.class);
  public static Map<Integer, WorkQueue.Task<?>> tasks = new HashMap<>();
  public static ConcurrentHashMap<Queue, QueueInfo> infoByQueue = new ConcurrentHashMap<>();

  public static class QueueInfo {
    public int maxThreads;
    public int spareThreads;
    public Set<Integer> runningTasks;
    public List<Reservation> reservations;

    public QueueInfo(
        int maxThreads,
        int spareThreads,
        Set<Integer> runningTasks,
        List<Reservation> reservations) {
      this.maxThreads = maxThreads;
      this.spareThreads = spareThreads;
      this.runningTasks = runningTasks;
      this.reservations = reservations;
    }

    public QueueInfo(int maxThreads) {
      this(maxThreads, maxThreads, new HashSet<>(), new ArrayList<>());
    }

    public boolean run(int taskId) {
      if (runningTasks.size() == maxThreads) {
        return false;
      }

      runningTasks.add(taskId);
      if (!reservations.isEmpty() && !canAllocate()) {
        runningTasks.remove((taskId));
        return false;
      }

      return true;
    }

    public void complete(int taskId) {
      runningTasks.remove(taskId);
    }

    public boolean ensureIdle(int threads) {
      return maxThreads - runningTasks.size() >= threads;
    }

    public void addReservation(Reservation incomingReservation) {
      reservations.add(incomingReservation);
      spareThreads -= incomingReservation.reservedCapacity();
    }

    public boolean canAllocate() {
      int normalAllocations = 0;
      Map<Reservation, Integer> reservedAllocations = new HashMap<>();

      for (Integer runningTaskId : runningTasks) {
        WorkQueue.Task<?> runningTask = tasks.get(runningTaskId);

        boolean allocatedToReservation = false;
        for (Reservation reservation : reservations) {
          if (reservation.matches(runningTask)) {
            int currentAllocation = reservedAllocations.getOrDefault(reservation, 0);
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

      return normalAllocations <= spareThreads;
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

  public static void initQueueWithCapacity(Queue q, int c) {
    infoByQueue.put(q, new QueueInfo(c));
  }

  public static void registerReservation(String qName, Reservation reservation) {
    Queue q = Queue.fromKey(qName);
    if (q == Queue.UNKNOWN) {
      return;
    }

    QueueInfo queueInfo = infoByQueue.get(q);
    if (queueInfo.spareThreads <= 1) {
      log.error(
          "Cannot enforce reservation for queue '{}' Requested: {} threads. No threads reserved.",
          qName,
          reservation.reservedCapacity());
      return;
    }

    if (reservation.reservedCapacity() > queueInfo.spareThreads - 1) {
      int capacityToReserve = queueInfo.spareThreads - 1;
      log.warn(
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

    tasks.put(task.getTaskId(), task);
    final AtomicBoolean acquired = new AtomicBoolean(false);
    infoByQueue.computeIfPresent(
        q,
        (queue, info) -> {
          acquired.setPlain(info.run(task.getTaskId()));
          return info;
        });

    if (!acquired.getPlain()) {
      tasks.remove(task.getTaskId());
    }

    return acquired.getPlain();
  }

  public static void release(WorkQueue.Task<?> task) {
    Queue q = Queue.fromKey(task.getQueueName());
    infoByQueue.computeIfPresent(
        q,
        (queue, info) -> {
          info.complete(task.getTaskId());
          return info;
        });

    tasks.remove(task.getTaskId());
  }

  public static boolean ensureIdle(Queue q, int c) {
    QueueInfo info = infoByQueue.get(q);
    if (info == null) {
      return true;
    }

    return info.ensureIdle(c);
  }
}
