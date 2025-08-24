package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.quota.TaskParser.user;

public class SoftMaxPerUserForQueue extends TaskQuota {
  public record NamespacedUser(String namespace, String user) {
    public NamespacedUser globalScoped() {
      return new NamespacedUser(GlobalQuotaSection.GLOBAL_QUOTA, user());
    }
  }

  private final Map<String, Integer> softMaxByNamespace;
  private final String queueName;
  private final Map<NamespacedUser, Integer> taskStartedCountByUser = new HashMap<>();
  private final Lock lock = new ReentrantLock();

  public SoftMaxPerUserForQueue(
      int maxPermits, Map<String, Integer> softMaxByNamespace, String queueName) {
    super(maxPermits);
    this.softMaxByNamespace = softMaxByNamespace;
    this.queueName = queueName;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queueName);
  }

  @Override
  public boolean tryAcquire(WorkQueue.Task<?> task, String namespace) {
    Optional<String> user = user(task);
    if (user.isEmpty()) {
      return true;
    }

    lock.lock();
    try {
      NamespacedUser namespacedUser = new NamespacedUser(user.get(), namespace);
      if (!tryAcquire(namespacedUser)) {
        return false;
      }

      if (tryAcquire(namespacedUser.globalScoped())) {
        return true;
      }

      release(namespacedUser);
      return false;
    } finally {
      lock.unlock();
    }
  }

  protected boolean tryAcquire(NamespacedUser user) {
    int runningTasks = taskStartedCountByUser.getOrDefault(user, 0);
    boolean overSoftLimit = runningTasks >= softMaxByNamespace.get(user.namespace());
    int permitCost = overSoftLimit ? 2 : 1;
    boolean acquired = false;

    if (permits.tryAcquire(permitCost)) {
      acquired = true;
      if (overSoftLimit) {
        permits.release(1);
      }
      runningTasks++;
      taskStartedCountByUser.put(user, runningTasks);
    }

    return acquired;
  }

  @Override
  public void release(WorkQueue.Task<?> task, String namespace) {
    Optional<String> user = user(task);
    if (user.isEmpty()) {
      return;
    }

    lock.lock();
    try {
      NamespacedUser namespacedUser = new NamespacedUser(user.get(), namespace);
      release(namespacedUser);
      release(namespacedUser.globalScoped());
    } finally {
      lock.unlock();
    }
  }

  protected void release(NamespacedUser user) {
    Integer count = taskStartedCountByUser.get(user);
    if (count != null) {
      permits.release();
      if (count == 1) {
        taskStartedCountByUser.remove(user);
      } else {
        taskStartedCountByUser.put(user, count - 1);
      }
    }
  }
}
