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
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TaskQuotaKeys {
  private static final Logger log = LoggerFactory.getLogger(TaskQuotaKeys.class);
  private static final String POOL_SECTION = "pool";
  private static final List<String> UNSUPPORTED_POOL_KEYS = List.of("min", "maxPerProject");

  private final MinStartForQueueQuota minStartForQueueQuota;
  private final MinStartForTaskForQueueQuota minStartForTaskForQueueQuota;
  private final UserResolver userResolver;

  @Inject
  public TaskQuotaKeys(
      MinStartForQueueQuota minStartForQueueQuota,
      MinStartForTaskForQueueQuota minStartForTaskForQueueQuota,
      UserResolver userResolver) {
    this.minStartForQueueQuota = minStartForQueueQuota;
    this.minStartForTaskForQueueQuota = minStartForTaskForQueueQuota;
    this.userResolver = userResolver;
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
                (section, cfg) ->
                    TaskQuotaForTaskForUserForQueue.build(section, cfg, pools, userResolver)),
            process(
                qs,
                TaskQuotaPerUserForTaskForQueue.KEY,
                (section, cfg) -> TaskQuotaPerUserForTaskForQueue.build(section, cfg, pools)),
            process(qs, SoftMaxPerUserForQueue.KEY, SoftMaxPerUserForQueue::build),
            process(qs, SoftMaxForTaskForQueue.KEY, SoftMaxForTaskForQueue::build),
            process(
                qs,
                SoftMaxForTaskForUserForQueue.KEY,
                (section, cfg) -> SoftMaxForTaskForUserForQueue.build(section, cfg, userResolver)),
            process(qs, SoftMaxPerUserForTaskForQueue.KEY, SoftMaxPerUserForTaskForQueue::build),
            process(qs, MinStartForQueueQuota.KEY, minStartForQueueQuota::build),
            process(qs, MinStartForTaskForQueueQuota.KEY, minStartForTaskForQueueQuota::build))
        .flatMap(List::stream)
        .toList();
  }

  /** Pools are declared as top-level {@code [pool "name"]} sections and referenced as pool:name. */
  public Map<String, Pool> buildPools(Config cfg) {
    Map<String, Pool> pools = new HashMap<>();
    for (String name : cfg.getSubsections(POOL_SECTION)) {
      for (String key : UNSUPPORTED_POOL_KEYS) {
        if (cfg.getString(POOL_SECTION, name, key) != null) {
          log.warn("Pool [{}]: {} is not supported yet and is ignored", name, key);
        }
      }
      pools.put(
          name,
          new Pool(name, poolLimit(cfg, name, "max"), poolLimit(cfg, name, "maxPerUser")));
    }
    return pools;
  }

  private static OptionalInt poolLimit(Config cfg, String name, String key) {
    return cfg.getString(POOL_SECTION, name, key) == null
        ? OptionalInt.empty()
        : OptionalInt.of(cfg.getInt(POOL_SECTION, name, key, 0));
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
