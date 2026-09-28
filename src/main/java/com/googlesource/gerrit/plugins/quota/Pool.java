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

/**
 * A named quota pool. Materializes exactly one tracker type on first use — once claimed as either a
 * {@link Permits} pool or a {@link PerUserTaskQuota} pool, the other accessor rejects the pool.
 */
final class Pool {
  final int max;
  private Permits permits;
  private PerUserTaskQuota perUser;

  Pool(int max) {
    this.max = max;
  }

  Permits permits() {
    if (perUser != null) {
      throw new IllegalStateException(
          "pool already claimed as a per-user pool; cannot mix with a Permits pool reference");
    }
    if (permits == null) {
      permits = new Permits(max);
    }
    return permits;
  }

  PerUserTaskQuota perUser() {
    if (permits != null) {
      throw new IllegalStateException(
          "pool already claimed as a Permits pool; cannot mix with a per-user pool reference");
    }
    if (perUser == null) {
      perUser = new PerUserTaskQuota((ids, task) -> ids.size() < max);
    }
    return perUser;
  }
}
