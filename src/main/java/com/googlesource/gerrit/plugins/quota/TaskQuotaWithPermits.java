package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TaskQuotaWithPermits implements TaskQuota {
  protected final AtomicInteger permits;

  public TaskQuotaWithPermits(int maxPermits) {
    this.permits = new AtomicInteger(maxPermits);
  }

  public boolean isReadyToStart(WorkQueue.Task<?> task) {
    if (permits.decrementAndGet() >= 0) {
      return true;
    }

    permits.incrementAndGet();
    return false;
  }

  public void onStop(WorkQueue.Task<?> task) {
    permits.incrementAndGet();
  }
}
