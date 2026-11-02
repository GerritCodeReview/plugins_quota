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

  protected ReservationBuilder reservation(QuotaSection qs) {
    return new ReservationBuilder(qs);
  }

  /** Reserves threads on a queue for the section's projects, and for the tasks matched. */
  public class ReservationBuilder {
    private final QuotaSection qs;
    private int capacity;
    private String queue;
    private Predicate<WorkQueue.Task<?>> tasks = task -> true;

    private ReservationBuilder(QuotaSection qs) {
      this.qs = qs;
    }

    public ReservationBuilder capacity(int threads) {
      this.capacity = threads;
      return this;
    }

    public ReservationBuilder onQueue(String queueName) {
      this.queue = queueName;
      return this;
    }

    /**
     * Narrows the reservation to the tasks the predicate accepts. The predicate needs no check on
     * the queue, as a reservation is only ever tested against the tasks running on the queue it was
     * registered on.
     */
    public ReservationBuilder forTasks(Predicate<WorkQueue.Task<?>> matcher) {
      this.tasks = matcher;
      return this;
    }

    public void register() {
      QueueManager.registerReservation(
          queue,
          new QueueManager.Reservation(
              capacity,
              task ->
                  tasks.test(task)
                      && projectResolver.estimateProject(task).map(qs::matches).orElse(false),
              qs.getNamespace()));
    }
  }
}
