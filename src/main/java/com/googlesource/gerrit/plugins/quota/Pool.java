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

import java.util.OptionalInt;

/**
 * A named quota pool declared as a {@code [pool "name"]} section. Holds a shared {@link Permits}
 * counter for {@code max} and a shared {@link PerUserTaskQuota} for {@code maxPerUser}, each
 * created on first use.
 */
final class Pool {
  private final String name;
  private final OptionalInt max;
  private final OptionalInt maxPerUser;
  private Permits permits;
  private PerUserTaskQuota perUser;

  Pool(String name, OptionalInt max, OptionalInt maxPerUser) {
    this.name = name;
    this.max = max;
    this.maxPerUser = maxPerUser;
  }

  Permits permits() {
    if (permits != null) {
      return permits;
    }

    if (max.isEmpty()) {
      throw new IllegalStateException("pool [%s] does not define max".formatted(name));
    }

    permits = new Permits(max.getAsInt());
    return permits;
  }

  PerUserTaskQuota perUser() {
    if (perUser != null) {
      return perUser;
    }

    if (maxPerUser.isEmpty()) {
      throw new IllegalStateException("pool [%s] does not define maxPerUser".formatted(name));
    }

    int limit = maxPerUser.getAsInt();
    perUser = new PerUserTaskQuota((ids, task) -> ids.size() < limit);
    return perUser;
  }

  int maxPerUser() {
    return maxPerUser.orElseThrow();
  }
}
