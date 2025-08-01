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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.googlesource.gerrit.plugins.quota.TaskParser.user;

public class PerUserTaskQuota {
  private final ConcurrentHashMap<String, Semaphore> quotaByUser = new ConcurrentHashMap<>();
  private final int maxPermits;

  public PerUserTaskQuota(int maxPermits) {
    this.maxPermits = maxPermits;
  }

  public boolean tryAcquire(WorkQueue.Task<?> task) {
    return user(task)
        .map(
            user -> {
              AtomicBoolean acquired = new AtomicBoolean(false);
              quotaByUser.compute(
                  user,
                  (key, semaphore) -> {
                    if (semaphore == null) {
                      semaphore = new Semaphore(maxPermits);
                    }
                    if (semaphore.tryAcquire()) {
                      acquired.setPlain(true);
                    }
                    return semaphore;
                  });
              return acquired.getPlain();
            })
        .orElse(true);
  }

  public void release(WorkQueue.Task<?> task) {
    user(task)
        .ifPresent(
            user ->
                quotaByUser.computeIfPresent(
                    user,
                    (u, quota) -> {
                      quota.release();
                      return quota.availablePermits() == maxPermits ? null : quota;
                    }));
  }
}
