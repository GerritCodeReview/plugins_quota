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

import com.google.gerrit.util.logging.NamedFluentLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.gerrit.server.git.WorkQueue.Task;

public class ParkedQuotaTransitionLogger {
  protected static final NamedFluentLogger quotaLog =
      NamedFluentLogger.forName(TaskQuotaLogFile.NAME);
  protected static final Map<Integer, TaskQuota> prevParkingQuotaByTaskId =
      new ConcurrentHashMap<>();
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

        @Override
        public String toString() {
          return "min start reservations";
        }
      };

  /** Logs only if the reason for parked changes from the previous parking event. */
  public static void logTaskWithEnforcedQuota(Task<?> t, TaskQuota q) {
    TaskQuota prevQuota = prevParkingQuotaByTaskId.put(t.getTaskId(), q);

    if (prevQuota == q) {
      return;
    }

    quotaLog.atInfo().log("Task [%s] parked due to quota rule [%s]", t, q);
  }

  public static void logTaskWithNoSatisfyingReservation(Task<?> t) {
    logTaskWithEnforcedQuota(t, CANNOT_SATISFY_RESERVATIONS_QUOTA);
  }

  public static void clear(Task<?> t) {
    prevParkingQuotaByTaskId.remove(t.getTaskId());
  }
}
