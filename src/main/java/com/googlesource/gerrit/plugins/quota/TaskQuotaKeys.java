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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TaskQuotaKeys {
  private static final Logger log = LoggerFactory.getLogger(TaskQuotaKeys.class);
  private static final String POOL_KEY = "countForPool";
  private static final Pattern POOL_PATTERN = Pattern.compile("(\\d+)\\s+([A-Za-z][\\w-]*)");

  private final MinStartForQueueQuota minStartForQueueQuota;
  private final MinStartForTaskForQueueQuota minStartForTaskForQueueQuota;

  @Inject
  public TaskQuotaKeys(
      MinStartForQueueQuota minStartForQueueQuota,
      MinStartForTaskForQueueQuota minStartForTaskForQueueQuota) {
    this.minStartForQueueQuota = minStartForQueueQuota;
    this.minStartForTaskForQueueQuota = minStartForTaskForQueueQuota;
  }

  public List<TaskQuota> buildQuotas(QuotaSection qs, Map<String, Pool> pools) {
    return Stream.of(
            process(
                qs,
                TaskQuotaForTaskForQueue.KEY,
                (section, cfg) -> TaskQuotaForTaskForQueue.build(section, cfg, pools)),
            process(
                qs,
                TaskQuotaForTaskForUserForQueue.KEY,
                (section, cfg) -> TaskQuotaForTaskForUserForQueue.build(section, cfg, pools)),
            process(
                qs,
                TaskQuotaPerUserForTaskForQueue.KEY,
                (section, cfg) -> TaskQuotaPerUserForTaskForQueue.build(section, cfg, pools)),
            process(qs, SoftMaxPerUserForQueue.KEY, SoftMaxPerUserForQueue::build),
            process(qs, SoftMaxForTaskForQueue.KEY, SoftMaxForTaskForQueue::build),
            process(qs, SoftMaxForTaskForUserForQueue.KEY, SoftMaxForTaskForUserForQueue::build),
            process(qs, SoftMaxPerUserForTaskForQueue.KEY, SoftMaxPerUserForTaskForQueue::build),
            process(qs, MinStartForQueueQuota.KEY, minStartForQueueQuota::build),
            process(qs, MinStartForTaskForQueueQuota.KEY, minStartForTaskForQueueQuota::build))
        .flatMap(List::stream)
        .toList();
  }

  /** Pools are declared only in the [global] section, but can be referenced from any section. */
  public Map<String, Pool> buildGlobalPools(QuotaSection qs) {
    if (!GlobalQuotaSection.GLOBAL_QUOTA.equals(qs.section())) {
      return Map.of();
    }

    Map<String, Pool> pools = new HashMap<>();
    for (String cfg : qs.cfg().getStringList(qs.section(), qs.subSection(), POOL_KEY)) {
      Matcher matcher = POOL_PATTERN.matcher(cfg);
      if (matcher.matches()) {
        pools.put(matcher.group(2), new Pool(Integer.parseInt(matcher.group(1))));
      } else {
        log.error("Invalid configuration entry [{}]", cfg);
      }
    }
    return pools;
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
