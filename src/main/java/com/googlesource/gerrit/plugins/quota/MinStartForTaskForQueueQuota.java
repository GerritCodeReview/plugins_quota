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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinStartForTaskForQueueQuota {
  public static final Logger log = LoggerFactory.getLogger(MinStartForTaskForQueueQuota.class);
  public static final String KEY = "minStartForTaskForQueue";

  // Pattern captures: 1. Count, 2. Task (word or ^regex$), 3. Queue Name
  public static final Pattern CONFIG_PATTERN = Pattern.compile("(\\d+)\\s+(\\^.*\\$|\\S+)\\s+(.+)");

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);

    if (qs instanceof GlobalQuotaSection || qs.isFallbackQuota()) {
      log.warn("minStartForTaskForQueue is not applicable in global and fallback quota sections");
      return Optional.empty();
    }

    if (matcher.matches()) {
      int reservation = Integer.parseInt(matcher.group(1));
      String taskMatchCriteria = matcher.group(2);
      String queue = matcher.group(3);

      // Pre-compile regex if the task criteria is a pattern
      final Pattern taskPattern = (taskMatchCriteria.startsWith("^") && taskMatchCriteria.endsWith("$"))
          ? Pattern.compile(taskMatchCriteria) : null;

      QueueManager.registerReservation(
          queue,
          new QueueManager.Reservation(
              reservation,
              task -> {
                // 1. Check Queue Match
                if (!task.getQueueName().equalsIgnoreCase(queue)) {
                  return false;
                }

                // 2. Check Task Match (Regex or Prefix)
                boolean taskMatch;
                if (taskPattern != null) {
                  taskMatch = taskPattern.matcher(task.toString()).find();
                } else {
                  taskMatch = task.toString().toLowerCase().contains(taskMatchCriteria.toLowerCase());
                }

                // 3. Check Project Match
                boolean projectMatch = TaskQuotas.estimateProject(task)
                                       .map(qs::matches).orElse(false);

                return taskMatch && projectMatch;
              },
              qs.getNamespace()));
    } else {
      log.error("Invalid configuration entry for minStartForTaskForQueue: [{}]", cfg);
    }

    return Optional.empty();
  }
}