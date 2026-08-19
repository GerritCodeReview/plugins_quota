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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TaskQuotaKeys {
  private final MinStartForQueueQuota minStartForQueueQuota;
  private final MinStartForTaskForQueueQuota minStartForTaskForQueueQuota;

  @Inject
  public TaskQuotaKeys(
      MinStartForQueueQuota minStartForQueueQuota,
      MinStartForTaskForQueueQuota minStartForTaskForQueueQuota) {
    this.minStartForQueueQuota = minStartForQueueQuota;
    this.minStartForTaskForQueueQuota = minStartForTaskForQueueQuota;
  }

  public List<TaskQuota> buildQuotas(QuotaSection qs) {
    return Stream.of(
            process(qs, TaskQuotaForTaskForQueue.KEY, TaskQuotaForTaskForQueue::build),
            process(
                qs, TaskQuotaForTaskForQueueForUser.KEY, TaskQuotaForTaskForQueueForUser::build),
            process(
                qs, TaskQuotaPerUserForTaskForQueue.KEY, TaskQuotaPerUserForTaskForQueue::build),
            process(qs, SoftMaxPerUserForQueue.KEY, SoftMaxPerUserForQueue::build),
            process(qs, SoftMaxForTaskForQueue.KEY, SoftMaxForTaskForQueue::build),
            process(qs, SoftMaxPerUserForTaskForQueue.KEY, SoftMaxPerUserForTaskForQueue::build),
            process(qs, MinStartForQueueQuota.KEY, minStartForQueueQuota::build),
            process(qs, MinStartForTaskForQueueQuota.KEY, minStartForTaskForQueueQuota::build))
        .flatMap(List::stream)
        .toList();
  }

  private List<TaskQuota> process(
      QuotaSection qs,
      String key,
      BiFunction<QuotaSection, String, Optional<TaskQuota>> processor) {
    return Arrays.stream(qs.cfg().getStringList(qs.section(), qs.subSection(), key))
        .map(cfg -> processor.apply(qs, cfg))
        .flatMap(Optional::stream)
        .toList();
  }
}
