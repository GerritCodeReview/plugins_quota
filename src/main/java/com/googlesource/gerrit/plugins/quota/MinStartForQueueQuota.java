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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinStartForQueueQuota {
  public static final Logger log = LoggerFactory.getLogger(MinStartForQueueQuota.class);
  public static final String KEY = "minStartForQueue";
  // 10 SSH-Interactive-Worker
  public static final Pattern CONFIG_PATTERN = Pattern.compile("(\\d+)\\s+(.+)");

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);

    if (qs instanceof GlobalQuotaSection || qs.isFallbackQuota()) {
      log.warn("minStartForQueueQuota is not applicable in global and fallback quota sections");
      return Optional.empty();
    }

    if (matcher.matches()) {
      int reservation = Integer.parseInt(matcher.group(1));
      String queue = matcher.group(2);
      QueueManager.registerReservation(
          queue,
          new QueueManager.Reservation(
              reservation,
              task -> {
                return task.getQueueName().equalsIgnoreCase(queue)
                    && TaskQuotas.estimateProject(task).map(qs::matches).orElse(false);
              }));
    } else {
      log.error("Invalid configuration entry [{}]", cfg);
    }

    return Optional.empty();
  }
}
