package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.quota.TaskParser.user;

public class SoftMaxPerUserForQueue extends TaskQuota {
  record NamespacedUser(String namespace, String user) {}

  private final Map<String, Integer> softMaxByNamespace;
  private final String queueName;
  private final ConcurrentHashMap<NamespacedUser, Integer> taskStartedCountByUser =
      new ConcurrentHashMap<>();

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
    return user(task)
        .map(
            user -> {
              AtomicBoolean acquired = new AtomicBoolean(false);
              taskStartedCountByUser.compute(
                  new NamespacedUser(user, namespace),
                  (key, val) -> {
                    int runningTasks = (val != null) ? val : 0;
                    boolean overSoftLimit = runningTasks >= softMaxByNamespace.get(namespace);
                    int permitCost = overSoftLimit ? 2 : 1;

                    if (permits.tryAcquire(permitCost)) {
                      acquired.setPlain(true);
                      if (overSoftLimit) {
                        permits.release(1);
                      }
                      ++runningTasks;
                    }
                    return runningTasks;
                  });
              return acquired.getPlain();
            })
        .orElse(true);
  }

  @Override
  public void release(WorkQueue.Task<?> task, String namespace) {
    user(task)
        .ifPresent(
            user ->
                taskStartedCountByUser.computeIfPresent(
                    new NamespacedUser(user, namespace),
                    (u, tasks) -> {
                      permits.release(1);
                      return tasks == 1 ? null : --tasks;
                    }));
  }
}
