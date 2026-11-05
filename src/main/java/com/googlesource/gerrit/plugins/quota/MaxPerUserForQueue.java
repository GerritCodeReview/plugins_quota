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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaxPerUserForQueue extends PerUserForQueue {
  private static final Logger log = LoggerFactory.getLogger(MaxPerUserForQueue.class);
  public static final String KEY = "maxStartPerUserForQueue";

  public MaxPerUserForQueue(QuotaSection quotaSection, int max, String queueName) {
    super(quotaSection, max, queueName);
  }

  @Override
  protected boolean canStart(int running) {
    return running < limit;
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    if (!matcher.matches()) {
      log.error("Invalid configuration entry for {} [{}]", KEY, cfg);
      return Optional.empty();
    }
    return Optional.of(
        new MaxPerUserForQueue(qs, Integer.parseInt(matcher.group(1)), matcher.group(2)));
  }

  @Override
  public String toString() {
    return KEY
        + ": max [%d], queue [%s], namespace [%s]"
            .formatted(limit, queue.getName(), quotaSection.getNamespace());
  }
}
