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

import java.util.List;

public interface QuotaSection {
  String KEY_MAX_PROJECTS = "maxProjects";
  String KEY_MAX_REPO_SIZE = "maxRepoSize";
  String KEY_MAX_TOTAL_SIZE = "maxTotalSize";

  String getNamespace();

  boolean matches(Project.NameKey project);

  Integer getMaxProjects();

  Long getMaxRepoSize();

  Long getMaxTotalSize();

  List<TaskQuota> getAllQuotas();
}
