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

import static com.google.gerrit.server.git.WorkQueue.Task;
import static com.googlesource.gerrit.plugins.quota.QueueManager.Queue;
import static com.googlesource.gerrit.plugins.quota.QueueManager.QueueInfo;

import com.google.gerrit.server.ioutil.HexFormat;
import com.google.gerrit.util.logging.NamedFluentLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParkedQuotaTransitionLogger {
  protected static final NamedFluentLogger quotaLog =
      NamedFluentLogger.forName(TaskQuotaLogFile.NAME);
  protected static final Map<Integer, TaskQuota> prevParkingQuotaByTaskId =
      new ConcurrentHashMap<>();
  protected static final Map<Integer, Instant> parkedSince = new ConcurrentHashMap<>();
  protected static final TaskQuota CANNOT_SATISFY_RESERVATIONS_QUOTA =
      new TaskQuota() {
        @Override
        public boolean isApplicable(Task<?> task) {
          throw new IllegalStateException();
        }

        @Override
        public boolean isReadyToStart(Task<?> task) {
          throw new IllegalStateException();
        }

        @Override
        public void onStop(Task<?> task) {
          throw new IllegalStateException();
        }
      };

  /** Logs only if the reason for parked changes from the previous parking event. */
  public static void logTaskWithEnforcedQuota(Task<?> t, TaskQuota q) {
    parkedSince.putIfAbsent(t.getTaskId(), Instant.now());
    if (shouldLog(t, q)) {
      quotaLog.atInfo().log("Task [%s] parked due to quota rule [%s]", formatTask(t), q);
    }
  }

  public static void logTaskWithNoSatisfyingReservation(Task<?> t) {
    parkedSince.putIfAbsent(t.getTaskId(), Instant.now());
    if (!shouldLog(t, CANNOT_SATISFY_RESERVATIONS_QUOTA)) {
      return;
    }

    QueueInfo queueInfo = QueueManager.infoByQueue.get(Queue.fromKey(t.getQueueName()));
    if (queueInfo != null) {
      queueInfo.reservations.stream()
          .filter(r -> r.matches(t))
          .findFirst()
          .ifPresentOrElse(
              reservation -> {
                quotaLog.atInfo().log(
                    "Task [%s] parked because there are no spare unreserved threads in queue [%s], "
                        + "and there are insufficient reserved threads for the %s namespace",
                    formatTask(t), t.getQueueName(), reservation.namespace());
              },
              () -> {
                quotaLog.atInfo().log(
                    "Task [%s] parked because there are no spare unreserved threads in queue [%s]",
                    formatTask(t), t.getQueueName());
              });
    }
  }

  public static void logOnTaskStartIfParked(Task<?> t) {
    if (!prevParkingQuotaByTaskId.containsKey(t.getTaskId())) {
      return;
    }

    quotaLog.atInfo().log(
        "Task %s is now unparked after %s",
        formatTask(t), Duration.between(parkedSince.get(t.getTaskId()), Instant.now()));
    clear(t);
  }

  public static boolean shouldLog(Task<?> t, TaskQuota q) {
    return prevParkingQuotaByTaskId.put(t.getTaskId(), q) != q;
  }

  public static void clear(Task<?> t) {
    prevParkingQuotaByTaskId.remove(t.getTaskId());
    parkedSince.remove(t.getTaskId());
  }

  public static String formatTask(Task<?> t) {
    return "%s: [%s]".formatted(HexFormat.fromInt(t.getTaskId()), t);
  }
}
