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

public class SoftMaxPerUserForQueue extends PerUserForQueue {
  public static final String KEY = "softMaxStartPerUserForQueue";

  public SoftMaxPerUserForQueue(QuotaSection quotaSection, int softMax, String queueName) {
    super(quotaSection, softMax, queueName);
  }

  @Override
  protected boolean canStart(int running) {
    return QueueManager.canStartWithinSoftMax(running, limit, queue);
  }

  public static Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    return matcher.find()
        ? Optional.of(
            new SoftMaxPerUserForQueue(qs, Integer.parseInt(matcher.group(1)), matcher.group(2)))
        : Optional.empty();
  }

  @Override
  public String toString() {
    return KEY
        + ": softMax [%d], queue [%s], namespace [%s]"
            .formatted(limit, queue.getName(), quotaSection.getNamespace());
  }
}
