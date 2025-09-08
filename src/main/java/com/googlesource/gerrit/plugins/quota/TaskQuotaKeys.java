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

import java.util.Optional;
import java.util.function.Function;

public enum TaskQuotaKeys {
  MAX_START_FOR_TASK_FOR_QUEUE("maxStartForTaskForQueue", TaskQuotaForTaskForQueue::build),
  MAX_START_FOR_TASK_FOR_USER_FOR_QUEUE(
      "maxStartForTaskForUserForQueue", TaskQuotaForTaskForQueueForUser::build),
  MAX_START_PER_USER_FOR_TASK_FOR_QUEUE(
      "maxStartPerUserForTaskForQueue", TaskQuotaPerUserForTaskForQueue::build),
  SOFT_MAX_START_FOR_QUEUE_PER_USER("softMaxStartPerUserForQueue", SoftMaxPerUserForQueue::build);

  public final String key;
  public final Function<TaskQuota.BuildInfo, Optional<TaskQuota>> processor;

  TaskQuotaKeys(String key, Function<TaskQuota.BuildInfo, Optional<TaskQuota>> processor) {
    this.key = key;
    this.processor = processor;
  }
}
