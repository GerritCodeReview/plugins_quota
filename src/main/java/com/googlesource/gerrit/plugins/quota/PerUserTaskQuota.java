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

import static com.googlesource.gerrit.plugins.quota.TaskParser.user;

import com.google.gerrit.server.git.WorkQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PerUserTaskQuota {
  private final ConcurrentHashMap<String, Set<Integer>> taskIdsByUser = new ConcurrentHashMap<>();
  private final int maxPermits;

  public PerUserTaskQuota(int maxPermits) {
    this.maxPermits = maxPermits;
  }

  public boolean tryAcquire(WorkQueue.Task<?> task) {
    return user(task)
        .map(
            user -> {
              AtomicBoolean acquired = new AtomicBoolean(false);
              taskIdsByUser.compute(
                  user,
                  (unused, ids) -> {
                    if (ids == null) {
                      ids = ConcurrentHashMap.newKeySet();
                    }
                    if (ids.size() < maxPermits) {
                      ids.add(task.getTaskId());
                      acquired.setPlain(true);
                    }
                    return ids;
                  });
              return acquired.getPlain();
            })
        .orElse(true);
  }

  public void release(WorkQueue.Task<?> task) {
    user(task)
        .ifPresent(
            user ->
                taskIdsByUser.computeIfPresent(
                    user,
                    (u, ids) -> {
                      ids.remove(task.getTaskId());
                      return ids.isEmpty() ? null : ids;
                    }));
  }
}
