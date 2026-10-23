// Copyright (C) 2026 The Android Open Source Project
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
import java.util.regex.Matcher;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinStartForTaskForQueueQuota extends MinStartQuota {
  private static final Logger log = LoggerFactory.getLogger(MinStartForTaskForQueueQuota.class);
  public static final String KEY = "minStartForTaskForQueue";

  @Inject
  public MinStartForTaskForQueueQuota(ProjectResolver projectResolver) {
    super(projectResolver);
  }

  @Override
  public Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    if (qs.isFallbackQuota()) {
      log.warn("minStartForTaskForQueue is not applicable in fallback quota section");
      return Optional.empty();
    }

    Matcher matcher = TaskQuotaForTaskForQueue.CONFIG_PATTERN.matcher(cfg);
    if (!matcher.matches()) {
      log.error("Invalid configuration entry for minStartForTaskForQueue: [{}]", cfg);
      return Optional.empty();
    }

    TaskGroup taskGroup = new TaskGroup(matcher.group(2));
    reservation(qs)
        .capacity(Integer.parseInt(matcher.group(1)))
        .onQueue(matcher.group(3))
        .forTasks(taskGroup::isApplicable)
        .register();

    return Optional.empty();
  }
}
