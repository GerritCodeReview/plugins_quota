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

public class MinStartQuota {
  public static final Logger log = LoggerFactory.getLogger(MinStartQuota.class);

  public static Optional<TaskQuota> build(String cfg) {
    Matcher matcher = TaskQuotaForTaskForQueue.CONFIG_PATTERN.matcher(cfg);
    if (matcher.matches()) {
      int limit = Integer.parseInt(matcher.group(1));
      String taskGroup = matcher.group(2);
      String queue = matcher.group(3);
      QueueStats.registerReservation(
          queue,
          new QueueStats.Reservation(
              limit,
              task -> new TaskQuotaForTaskForQueue(queue, taskGroup, limit).isApplicable(task)));
    } else {
      log.error("Invalid configuration entry [{}]", cfg);
    }

    return Optional.empty();
  }
}
