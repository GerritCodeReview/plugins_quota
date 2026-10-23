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
import java.util.function.Predicate;

/**
 * Common handling of the minStart quotas, which reserve capacity on a queue rather than cap it. The
 * reservation is registered on the queue itself, so there is no quota left to enforce per task.
 */
public abstract class MinStartQuota {
  protected final ProjectResolver projectResolver;

  protected MinStartQuota(ProjectResolver projectResolver) {
    this.projectResolver = projectResolver;
  }

  public abstract Optional<TaskQuota> build(QuotaSection qs, String cfg);

  /**
   * Reserves capacity for the section's projects on the queue, narrowed further by the matcher. The
   * matcher needs no check of its own on the queue, as a reservation is only ever tested against
   * the tasks running on the queue it was registered on.
   */
  protected Optional<TaskQuota> reserve(
      QuotaSection qs, int capacity, String queue, Predicate<WorkQueue.Task<?>> matcher) {
    QueueManager.registerReservation(
        queue,
        new QueueManager.Reservation(
            capacity,
            task ->
                matcher.test(task)
                    && projectResolver.estimateProject(task).map(qs::matches).orElse(false),
            qs.getNamespace()));

    return Optional.empty();
  }
}
