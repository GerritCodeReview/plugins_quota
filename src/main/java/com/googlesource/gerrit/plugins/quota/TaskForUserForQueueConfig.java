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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration of the quotas scoped to a task group, a user and a queue, i.e. {@code <limit>
 * <taskGroup> <user> <queue>}.
 */
public class TaskForUserForQueueConfig {
  private static final Logger log = LoggerFactory.getLogger(TaskForUserForQueueConfig.class);

  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+)\\s+"
              + TaskParser.TASK_GROUP_PATTERN
              + "\\s+"
              + TaskParser.USER_PATTERN
              + "\\s+(.+)");

  public interface Factory {
    TaskQuota create(
        QuotaSection quotaSection, String queueName, String user, String taskGroup, int limit);
  }

  public static Optional<TaskQuota> build(
      QuotaSection qs, String cfg, String key, Factory factory) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    if (!matcher.matches()) {
      log.error("Invalid configuration entry for {} [{}]", key, cfg);
      return Optional.empty();
    }
    return Optional.of(
        factory.create(
            qs,
            matcher.group(4),
            matcher.group(3),
            matcher.group(2),
            Integer.parseInt(matcher.group(1))));
  }

  private TaskForUserForQueueConfig() {}
}
