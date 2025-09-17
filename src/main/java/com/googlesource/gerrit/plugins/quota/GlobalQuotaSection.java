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

/**
 * Provides the representation of global-quota config
 *
 * @param cfg quota.cfg file
 */
public record GlobalQuotaSection(Config cfg) implements QuotaSection {
  public static final String GLOBAL_QUOTA = "global-quota";

  public String getNamespace() {
    return GLOBAL_QUOTA;
  }

  public boolean matches(Project.NameKey project) {
    return true;
  }

  @Override
  public String section() {
    return GLOBAL_QUOTA;
  }

  @Override
  public String subSection() {
    return null;
  }
}
