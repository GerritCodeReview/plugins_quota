package com.googlesource.gerrit.plugins.quota;

import java.util.Optional;
import java.util.function.Function;

public enum TaskQuotaKeys {
  MAX_START_FOR_TASK_FOR_QUEUE("maxStartForTaskForQueue", TaskQuotaForTaskForQueue::build),
  MAX_START_FOR_TASK_FOR_USER_FOR_QUEUE(
      "maxStartForTaskForUserForQueue", TaskQuotaForTaskForQueueForUser::build);

  private final String key;
  private final Function<String, Optional<TaskQuota>> processor;

  TaskQuotaKeys(String key, Function<String, Optional<TaskQuota>> processor) {
    this.key = key;
    this.processor = processor;
  }

  public String getKey() {
    return key;
  }

  public Function<String, Optional<TaskQuota>> getProcessor() {
    return processor;
  }
}
