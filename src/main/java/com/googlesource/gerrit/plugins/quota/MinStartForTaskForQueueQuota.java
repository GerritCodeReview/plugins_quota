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
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinStartForTaskForQueueQuota {
  public static final Logger log = LoggerFactory.getLogger(MinStartForTaskForQueueQuota.class);
  public static final String KEY = "minStartForTaskForQueue";
  private final ProjectResolver projectResolver;

  @Inject
  public MinStartForTaskForQueueQuota(ProjectResolver projectResolver) {
    this.projectResolver = projectResolver;
  }

  public Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = TaskQuotaForTaskForQueue.CONFIG_PATTERN.matcher(cfg);

    if (qs instanceof GlobalQuotaSection || qs.isFallbackQuota()) {
      log.warn("minStartForTaskForQueue is not applicable in global and fallback quota sections");
      return Optional.empty();
    }

    if (matcher.matches()) {
      int reservation = Integer.parseInt(matcher.group(1));
      String taskMatchCriteria = matcher.group(2);
      String queue = matcher.group(3);

      TaskGroup taskGroup = new TaskGroup(taskMatchCriteria);
      QueueManager.registerReservation(
          queue,
          new QueueManager.Reservation(
              reservation,
              task -> {
                if (!task.getQueueName().equalsIgnoreCase(queue)) {
                  return false;
                }

                boolean taskMatch = taskGroup.isApplicable(task);
                if (!taskMatch) {
                  return false;
                }

                return projectResolver.estimateProject(task).map(qs::matches).orElse(false);
              },
              qs.getNamespace()));
    } else {
      log.error("Invalid configuration entry for minStartForTaskForQueue: [{}]", cfg);
    }

    return Optional.empty();
  }
}
