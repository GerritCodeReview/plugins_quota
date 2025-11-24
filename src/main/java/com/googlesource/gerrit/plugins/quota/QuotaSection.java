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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

public interface QuotaSection {
  String KEY_MAX_PROJECTS = "maxProjects";
  String KEY_MAX_REPO_SIZE = "maxRepoSize";
  String KEY_MAX_TOTAL_SIZE = "maxTotalSize";
  String KEY_QUOTA_SIZE_EXCEEDED_MESSAGE = "quotaSizeExceededMessage";

  String DEFAULT_QUOTA_SIZE_MESSAGE_TEMPLATE =
      "Project ${project} exceeds quota: max=${maximum} bytes, available=${available} bytes.";

  String getNamespace();

  boolean matches(Project.NameKey project);

  default Integer getMaxProjects() {
    if (!cfg().getNames(section(), subSection()).contains(KEY_MAX_PROJECTS)) {
      return null;
    }
    return cfg().getInt(section(), subSection(), KEY_MAX_PROJECTS, Integer.MAX_VALUE);
  }

  default Long getMaxRepoSize() {
    if (!cfg().getNames(section(), subSection()).contains(KEY_MAX_REPO_SIZE)) {
      return null;
    }
    return cfg().getLong(section(), subSection(), KEY_MAX_REPO_SIZE, Long.MAX_VALUE);
  }

  default Long getMaxTotalSize() {
    if (!cfg().getNames(section(), subSection()).contains(KEY_MAX_TOTAL_SIZE)) {
      return null;
    }
    return cfg().getLong(section(), subSection(), KEY_MAX_TOTAL_SIZE, Long.MAX_VALUE);
  }

  default List<TaskQuota> getAllQuotas() {
    return Arrays.stream(TaskQuotaKeys.values())
        .flatMap(
            type ->
                Arrays.stream(cfg().getStringList(section(), subSection(), type.key))
                    .map(cfg -> type.processor.apply(this, cfg))
                    .flatMap(Optional::stream))
        .toList();
  }

  default String quotaSizeExceededMessage(
      Project.NameKey project, long availableSize, long maximumSize) {
    return QuotaSizeMessageInterpolator.interpolate(
        getQuotaSizeExceededMessageTemplate(), project, availableSize, maximumSize);
  }

  private String getQuotaSizeExceededMessageTemplate() {
    String tpl = cfg().getString(section(), subSection(), KEY_QUOTA_SIZE_EXCEEDED_MESSAGE);
    if (tpl == null || tpl.trim().isEmpty()) {
      return DEFAULT_QUOTA_SIZE_MESSAGE_TEMPLATE;
    }
    return tpl;
  }

  default boolean isFallbackQuota() {
    return false;
  }

  Config cfg();

  String section();

  String subSection();
}
