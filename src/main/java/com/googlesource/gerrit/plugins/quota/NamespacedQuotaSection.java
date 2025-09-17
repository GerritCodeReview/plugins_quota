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

public record NamespacedQuotaSection(Config cfg, String namespace, String resolvedNamespace)
    implements QuotaSection {
  public static final String QUOTA = "quota";

  public NamespacedQuotaSection(Config cfg, String namespace) {
    this(cfg, namespace, namespace);
  }

  public String getNamespace() {
    return resolvedNamespace;
  }

  public boolean matches(Project.NameKey project) {
    return new Namespace(resolvedNamespace).matches(project);
  }

  @Override
  public String section() {
    return QUOTA;
  }

  @Override
  public String subSection() {
    return namespace();
  }
}
