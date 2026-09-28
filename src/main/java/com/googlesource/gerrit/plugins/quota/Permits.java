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

import java.util.concurrent.atomic.AtomicInteger;

/** A counter that can be owned by a single {@link TaskQuotaWithPermits} or shared by several. */
final class Permits {
  private final AtomicInteger available;
  private final int max;

  Permits(int max) {
    this.available = new AtomicInteger(max);
    this.max = max;
  }

  boolean tryAcquire() {
    if (available.decrementAndGet() >= 0) {
      return true;
    }
    available.incrementAndGet();
    return false;
  }

  void release() {
    available.incrementAndGet();
  }

  int max() {
    return max;
  }
}
