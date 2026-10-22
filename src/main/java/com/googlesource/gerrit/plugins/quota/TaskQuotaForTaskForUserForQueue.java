// Copyright (C) 2014 The Android Open Source Project
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

public class TaskQuotaForTaskForUserForQueue extends TaskQuotaForTaskForQueue {
  private static final Logger log = LoggerFactory.getLogger(TaskQuotaForTaskForUserForQueue.class);
  public static final String KEY = "maxStartForTaskForUserForQueue";
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+|"
              + POOL_PREFIX
              + "[A-Za-z][\\w-]*)\\s+"
              + TaskParser.TASK_GROUP_PATTERN
              + "\\s+"
              + TaskParser.USER_PATTERN
              + "\\s+(.+)");

  private final String user;

  public TaskQuotaForTaskForUserForQueue(
      QuotaSection quotaSection, String queueName, String user, String taskGroup, int maxStart) {
    super(quotaSection, queueName, taskGroup, maxStart);
    this.user = user;
  }

  public TaskQuotaForTaskForUserForQueue(
      QuotaSection quotaSection, String queueName, String user, String taskGroup, Permits pooled) {
    super(quotaSection, queueName, taskGroup, pooled);
    this.user = user;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return TaskParser.isUser(task, user) && super.isApplicable(task);
  }

  public static Optional<TaskQuota> build(
      QuotaSection qs, String cfg, Map<String, Pool> pools, UserResolver userResolver) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    if (!matcher.matches()) {
      log.error("Invalid configuration entry for {} [{}]", KEY, cfg);
      return Optional.empty();
    }
    String token = matcher.group(1);
    String taskGroup = matcher.group(2);
    String queueName = matcher.group(4);
    Optional<String> user = userResolver.storedUsername(matcher.group(3), KEY);
    if (user.isEmpty()) {
      return Optional.empty();
    }
    if (token.startsWith(POOL_PREFIX)) {
      String poolName = token.substring(POOL_PREFIX.length());
      Pool pool = pools.get(poolName);
      if (pool == null) {
        log.error("Unknown quota pool [{}] referenced in [{}]", poolName, cfg);
        return Optional.empty();
      }
      try {
        return Optional.of(
            new TaskQuotaForTaskForUserForQueue(
                qs, queueName, user.get(), taskGroup, pool.permits()));
      } catch (IllegalStateException e) {
        log.error("Invalid pool reference in [{}]: {}", cfg, e.getMessage());
        return Optional.empty();
      }
    }
    return Optional.of(
        new TaskQuotaForTaskForUserForQueue(
            qs, queueName, user.get(), taskGroup, Integer.parseInt(token)));
  }

  @Override
  public String toString() {
    return KEY
        + ": task [%s], user [%s], queue [%s], permits [%d], namespace [%s]"
            .formatted(taskGroup, user, queueName, permits.max(), quotaSection.getNamespace());
  }
}
