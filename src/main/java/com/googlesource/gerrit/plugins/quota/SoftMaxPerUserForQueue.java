package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.quota.TaskParser.user;

public class SoftMaxPerUserForQueue extends TaskQuota {
  public static final Map<String, Function<BuildInfo, Integer>> QUEUES =
      Map.of(
          "SSH-Interactive-Worker",
          BuildInfo::interactiveThreads,
          "SSH-Batch-Worker",
          BuildInfo::batchThreads);
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile("(\\d+)\\s+(" + String.join("|", QUEUES.keySet()) + ")");
  private final int softMax;
  private final String queueName;
  private final ConcurrentHashMap<String, Integer> tasksByUser = new ConcurrentHashMap<>();
  private final Set<Integer> softAcquiredTasks = new HashSet<>();

  public SoftMaxPerUserForQueue(int maxPermits, int softMax, String queueName) {
    super(maxPermits);
    this.softMax = softMax;
    this.queueName = queueName;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return task.getQueueName().equals(queueName);
  }

  @Override
  public boolean tryAcquire(WorkQueue.Task<?> task) {
    return user(task)
        .map(
            user -> {
              AtomicBoolean acquired = new AtomicBoolean(false);
              tasksByUser.compute(
                  user,
                  (key, val) -> {
                    int runningTasks = (val != null) ? val : 0;
                    boolean overSoftLimit = runningTasks >= softMax;
                    int permitCost = overSoftLimit ? 2 : 1;

                    if (permits.tryAcquire(permitCost)) {
                      acquired.setPlain(true);
                      if (overSoftLimit) {
                        softAcquiredTasks.add(task.getTaskId());
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
  public void onNotReadyToStart(WorkQueue.Task<?> task) {
    if (softAcquiredTasks.contains(task.getTaskId())) {
      release(task, 2);
    } else {
      release(task, 1);
    }
  }

  @Override
  public void onTaskStart(WorkQueue.Task<?> task) {
    if (softAcquiredTasks.contains(task.getTaskId())) {
      permits.release();
    }
  }

  @Override
  public void release(WorkQueue.Task<?> task) {
    release(task, 1);
  }

  private void release(WorkQueue.Task<?> task, int releasePermits) {
    user(task)
        .ifPresent(
            user ->
                tasksByUser.computeIfPresent(
                    user,
                    (u, tasks) -> {
                      permits.release(releasePermits);
                      return tasks == 1 ? null : --tasks;
                    }));
  }

  public static Optional<TaskQuota> build(BuildInfo buildInfo) {
    Matcher matcher = CONFIG_PATTERN.matcher(buildInfo.config());
    return matcher.find()
        ? Optional.of(
            new SoftMaxPerUserForQueue(
                QUEUES.get(matcher.group(2)).apply(buildInfo),
                Integer.parseInt(matcher.group(1)),
                matcher.group(2)))
        : Optional.empty();
  }
}
