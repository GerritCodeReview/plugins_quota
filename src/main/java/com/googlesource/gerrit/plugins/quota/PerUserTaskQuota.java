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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerUserTaskQuota {
  public static final Pattern USER_EXTRACT_PATTERN = Pattern.compile("\\(([a-z0-9]+)\\)$");
  private final ConcurrentHashMap<String, Semaphore> quotasByUser = new ConcurrentHashMap<>();
  private final int permits;

  public PerUserTaskQuota(int permits) {
    this.permits = permits;
  }

  public boolean tryAcquire(WorkQueue.Task<?> task) {
    Optional<String> user = user(task);
    return user.map(
            s -> quotasByUser.computeIfAbsent(s, ignore -> new Semaphore(permits)).tryAcquire())
        .orElse(true);
  }

  public synchronized void release(WorkQueue.Task<?> task) {
    Optional<String> user = user(task);
    if (user.isPresent()) {
      Semaphore s = quotasByUser.get(user.get());
      s.release();
      if (s.availablePermits() == permits) {
        quotasByUser.remove(user.get());
      }
    }
  }

  private Optional<String> user(WorkQueue.Task<?> task) {
    Matcher matcher = USER_EXTRACT_PATTERN.matcher(task.toString());
    return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
  }
}
