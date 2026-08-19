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

import com.google.gerrit.server.git.WorkQueue;
import java.util.Optional;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoftMaxForTaskForQueueForUser extends SoftMaxForTaskForQueue {
  public static final Logger log = LoggerFactory.getLogger(SoftMaxForTaskForQueueForUser.class);
  public static final String KEY = "softMaxStartForTaskForUserForQueue";

  private final String user;

  public SoftMaxForTaskForQueueForUser(
      QuotaSection quotaSection, String queueName, String user, String taskGroup, int softMax) {
    super(quotaSection, queueName, taskGroup, softMax);
    this.user = user;
  }

  @Override
  public boolean isApplicable(WorkQueue.Task<?> task) {
    return TaskParser.user(task).map(user::equals).orElse(false) && super.isApplicable(task);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = TaskQuotaForTaskForQueueForUser.CONFIG_PATTERN.matcher(cfg);
    if (matcher.matches()) {
      return Optional.of(
          new SoftMaxForTaskForQueueForUser(
              qs,
              matcher.group(4),
              matcher.group(3),
              matcher.group(2),
              Integer.parseInt(matcher.group(1))));
    } else {
      log.error("Invalid configuration entry for softMaxStartForTaskForUserForQueue [{}]", cfg);
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return KEY
        + ": softMax [%d], task [%s], user [%s], queue [%s], namespace [%s]"
            .formatted(softMax, taskGroup, user, queueName, quotaSection.getNamespace());
  }
}
