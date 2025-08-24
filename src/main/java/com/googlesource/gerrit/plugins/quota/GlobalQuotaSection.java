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

import com.google.gerrit.entities.Project;
import org.eclipse.jgit.lib.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record GlobalQuotaSection(Config cfg) implements QuotaSection {
  public static final String GLOBAL_QUOTA = "global-quota";

  @Override
  public String getNamespace() {
    return GLOBAL_QUOTA;
  }

  @Override
  public boolean matches(Project.NameKey project) {
    return true;
  }

  @Override
  public Integer getMaxProjects() {
    if (!cfg.getNames(GLOBAL_QUOTA).contains(KEY_MAX_PROJECTS)) {
      return null;
    }
    return cfg.getInt(GLOBAL_QUOTA, KEY_MAX_PROJECTS, Integer.MAX_VALUE);
  }

  @Override
  public Long getMaxRepoSize() {
    if (!cfg.getNames(GLOBAL_QUOTA).contains(KEY_MAX_REPO_SIZE)) {
      return null;
    }
    return cfg.getLong(GLOBAL_QUOTA, KEY_MAX_REPO_SIZE, Long.MAX_VALUE);
  }

  @Override
  public Long getMaxTotalSize() {
    if (!cfg.getNames(GLOBAL_QUOTA).contains(KEY_MAX_TOTAL_SIZE)) {
      return null;
    }
    return cfg.getLong(GLOBAL_QUOTA, KEY_MAX_TOTAL_SIZE, Long.MAX_VALUE);
  }

  public List<TaskQuota> getAllQuotas() {
    return Arrays.stream(TaskQuotaKeys.values())
        .flatMap(
            type ->
                Arrays.stream(cfg.getStringList(GLOBAL_QUOTA, null, type.key))
                    .map(cfgLine -> new TaskQuota.BuildInfo(cfgLine, GLOBAL_QUOTA))
                    .map(type.processor)
                    .flatMap(Optional::stream))
        .toList();
  }
}
