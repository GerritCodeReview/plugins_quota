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

/**
 * Base class of the quotas configured with a minStart key.
 *
 * <p>A minStart quota caps nothing. It holds back threads on a queue for the tasks it matches, so
 * that those tasks can still start once the rest of the queue is saturated. {@link QueueManager}
 * enforces the reservation when it hands out threads, which is why {@link #build} registers one and
 * returns no {@link TaskQuota} of its own.
 */
public abstract class MinStartQuota {
  protected final ProjectResolver projectResolver;

  protected MinStartQuota(ProjectResolver projectResolver) {
    this.projectResolver = projectResolver;
  }

  public abstract Optional<TaskQuota> build(QuotaSection qs, String cfg);

  /** Starts a reservation held for the section's projects, which a variant narrows further. */
  protected QueueManager.Reservation.Builder reservation(QuotaSection qs) {
    return QueueManager.Reservation.builder()
        .inNamespace(qs.getNamespace())
        .matching(task -> projectResolver.estimateProject(task).map(qs::matches).orElse(false));
  }
}
