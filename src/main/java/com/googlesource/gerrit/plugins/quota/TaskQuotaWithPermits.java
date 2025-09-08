package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;
import java.util.concurrent.Semaphore;

public abstract class TaskQuotaWithPermits implements TaskQuota {
  protected final Semaphore permits;

  public TaskQuotaWithPermits(int maxPermits) {
    this.permits = new Semaphore(maxPermits);
  }

  public boolean tryAcquire(WorkQueue.Task<?> task) {
    return permits.tryAcquire();
  }

  public void release(WorkQueue.Task<?> task) {
    permits.release();
  }
}
