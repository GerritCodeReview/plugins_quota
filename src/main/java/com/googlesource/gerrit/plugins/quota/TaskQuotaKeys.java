package com.googlesource.gerrit.plugins.quota;

import java.util.Optional;
import java.util.function.Function;

public enum TaskQuotaKeys {
  MAX_START_FOR_TASK_FOR_QUEUE("maxStartForTaskForQueue", TaskQuotaForTaskForQueue::build),
  MAX_START_FOR_TASK_FOR_USER_FOR_QUEUE(
      "maxStartForTaskForUserForQueue", TaskQuotaForTaskForQueueForUser::build),
  MAX_START_PER_USER_FOR_TASK_FOR_QUEUE(
      "maxStartPerUserForTaskForQueue", TaskQuotaPerUserForTaskForQueue::build);

  public final String key;
  public final Function<String, Optional<TaskQuota>> processor;

  TaskQuotaKeys(String key, Function<String, Optional<TaskQuota>> processor) {
    this.key = key;
    this.processor = processor;
  }
}
