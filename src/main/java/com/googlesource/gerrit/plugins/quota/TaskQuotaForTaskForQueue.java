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

import com.google.gerrit.server.git.WorkQueue;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskQuotaForTaskForQueue extends TaskQuotaForTask {
  public static final Logger log = LoggerFactory.getLogger(TaskQuotaForTaskForQueue.class);
  public static final String KEY = "maxStartForTaskForQueue";
  public static final String POOL_PREFIX = "pool:";
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+|"
              + POOL_PREFIX
              + "[A-Za-z][\\w-]*)\\s+"
              + TaskParser.TASK_GROUP_PATTERN
              + "\\s+(.+)");
  public final String queueName;
  protected final QuotaSection quotaSection;

  public TaskQuotaForTaskForQueue(
      QuotaSection quotaSection, String queueName, String taskGroup, int maxStart) {
    super(taskGroup, maxStart);
    this.quotaSection = quotaSection;
    this.queueName = queueName;
  }

  public TaskQuotaForTaskForQueue(
      QuotaSection quotaSection, String queueName, String taskGroup, Permits pooled) {
    super(taskGroup, pooled);
    this.quotaSection = quotaSection;
    this.queueName = queueName;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return super.isApplicable(task) && task.getQueueName().equals(queueName);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg, Map<String, Pool> pools) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    if (!matcher.matches()) {
      log.error("Invalid configuration entry [{}]", cfg);
      return Optional.empty();
    }
    String token = matcher.group(1);
    String taskGroup = matcher.group(2);
    String queueName = matcher.group(3);
    if (token.startsWith(POOL_PREFIX)) {
      String poolName = token.substring(POOL_PREFIX.length());
      Pool pool = pools.get(poolName);
      if (pool == null) {
        log.error("Unknown quota pool [{}] referenced in [{}]", poolName, cfg);
        return Optional.empty();
      }
      try {
        return Optional.of(new TaskQuotaForTaskForQueue(qs, queueName, taskGroup, pool.permits()));
      } catch (IllegalStateException e) {
        log.error("Invalid pool reference in [{}]: {}", cfg, e.getMessage());
        return Optional.empty();
      }
    }
    return Optional.of(
        new TaskQuotaForTaskForQueue(qs, queueName, taskGroup, Integer.parseInt(token)));
  }

  @Override
  public String toString() {
    return KEY
        + ": task [%s], queue [%s], permits [%d], namespace [%s]"
            .formatted(taskGroup, queueName, permits.max(), quotaSection.getNamespace());
  }
}
