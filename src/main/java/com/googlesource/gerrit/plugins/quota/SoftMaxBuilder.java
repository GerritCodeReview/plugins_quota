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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoftMaxBuilder {
  public static final String INTERACTIVE_WORKER = "SSH-Interactive-Worker";
  public static final String BATCH_WORKER = "SSH-Batch-Worker";
  public static final Map<String, Map<String, Integer>> SOFT_MAXES_BY_QUEUE_BY_NAMESPACE =
      Map.of(INTERACTIVE_WORKER, new HashMap<>(), BATCH_WORKER, new HashMap<>());
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "(\\d+)\\s+(" + String.join("|", SOFT_MAXES_BY_QUEUE_BY_NAMESPACE.keySet()) + ")");

  public record ThreadSizes(int interactiveThreads, int batchThreads) {
    public int getQueueSize(String queue) {
      return switch (queue) {
        case INTERACTIVE_WORKER -> interactiveThreads();
        case BATCH_WORKER -> batchThreads();
        default -> throw new IllegalStateException("Unexpected queue: " + queue);
      };
    }
  }

  public static Optional<TaskQuota> record(TaskQuota.BuildInfo buildInfo) {
    Matcher matcher = CONFIG_PATTERN.matcher(buildInfo.config());
    if (matcher.find()) {
      SOFT_MAXES_BY_QUEUE_BY_NAMESPACE
          .get(matcher.group(2))
          .put(buildInfo.namespace(), Integer.parseInt(matcher.group(1)));
    }

    return Optional.empty();
  }

  public static List<SoftMaxPerUserForQueue> build(ThreadSizes threadSizes) {
    List<SoftMaxPerUserForQueue> result = new ArrayList<>();

    for (Map.Entry<String, Map<String, Integer>> entry :
        SOFT_MAXES_BY_QUEUE_BY_NAMESPACE.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        result.add(
            new SoftMaxPerUserForQueue(
                threadSizes.getQueueSize(entry.getKey()), entry.getValue(), entry.getKey()));
      }
    }

    return result;
  }
}
