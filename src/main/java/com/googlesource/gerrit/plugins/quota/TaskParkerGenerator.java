package com.googlesource.gerrit.plugins.quota;

import com.google.common.base.Strings;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import org.eclipse.jgit.lib.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Semaphore;

@Singleton
public class TaskParkerGenerator {
  private final ProjectCache projectCache;
  private static final String TASK_BUDGET_SECTION = "taskBudget";
  private static final String QUOTA_CFG_FILE = "quota.config";
  private static final String QUEUE = "queue";
  private static final String TASK = "task";
  private static final String LIMIT = "task";

  @Inject
  public TaskParkerGenerator(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  Map<String, WorkQueue.TaskParker> generate() {
    Config cfg = projectCache.getAllProjects().getConfig(QUOTA_CFG_FILE).get();
    Set<String> subSections = cfg.getSubsections(TASK_BUDGET_SECTION);

    if (subSections.isEmpty()) {
      return Map.of();
    }

    Map<String, WorkQueue.TaskParker> parkers = new HashMap<>();
    for (String subSection : subSections) {
      String[] queues = cfg.getStringList(TASK_BUDGET_SECTION, subSection, QUEUE);
      String[] tasks = cfg.getStringList(TASK_BUDGET_SECTION, subSection, TASK);
      int limit = cfg.getInt(TASK_BUDGET_SECTION, subSection, LIMIT, -1);

      if(limit == -1 || tasks.length != 1) {
        throw new RuntimeException(String.format("Invalid format for file: %s", QUOTA_CFG_FILE));
      }

      parkers.put(subSection.replaceAll(" ", "_"), generate(Arrays.asList(queues), tasks[0], limit));
    }

    return parkers;
  }



  WorkQueue.TaskParker generate(List<String> queues, String taskName, int limit) {
    return new WorkQueue.TaskParker() {
      final Semaphore semaphore = new Semaphore(limit);

      @Override
      public void onStart(WorkQueue.Task<?> task) {}

      @Override
      public void onStop(WorkQueue.Task<?> task) {
        if(!doesApply(task)) {
          return;
        }

        semaphore.release();
      }

      @Override
      public boolean isReadyToStart(WorkQueue.Task<?> task) {
        if(!doesApply(task)) {
          return true;
        }

        return semaphore.tryAcquire();
      }

      @Override
      public void onNotReadyToStart(WorkQueue.Task<?> task) {
        if(!doesApply(task)) {
          return;
        }

        semaphore.release();
      }

      private boolean doesApply(WorkQueue.Task<?> task) {
        return (Strings.isNullOrEmpty(taskName) || taskName.equals(task.toString())) &&
            (queues.isEmpty() || queues.stream().anyMatch(q -> q.equals(task.getQueueName())));
      }
    };
  }
}
