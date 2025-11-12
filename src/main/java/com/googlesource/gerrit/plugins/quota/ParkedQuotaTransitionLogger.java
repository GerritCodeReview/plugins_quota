package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.util.logging.NamedFluentLogger;

import java.util.HashMap;
import java.util.Map;

import static com.google.gerrit.server.git.WorkQueue.Task;

public class ParkedQuotaTransitionLogger {
  protected static final NamedFluentLogger quotaLog =
      NamedFluentLogger.forName(TaskQuotaLogFile.NAME);
  protected static final Map<Integer, TaskQuota> prevParkingQuota = new HashMap<>();
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
    if (prevParkingQuota.containsKey(t.getTaskId()) && prevParkingQuota.get(t.getTaskId()) == q) {
      return;
    }

    quotaLog.atInfo().log("Task [%s] will be parked due to quota rule [%s]", t, q);
    prevParkingQuota.put(t.getTaskId(), q);
  }

  public static void logTaskWithNoSatisfyingReservation(Task<?> t) {
    logTaskWithEnforcedQuota(t, CANNOT_SATISFY_RESERVATIONS_QUOTA);
  }

  public static void clear(Task<?> t) {
    prevParkingQuota.remove(t.getTaskId());
  }
}
