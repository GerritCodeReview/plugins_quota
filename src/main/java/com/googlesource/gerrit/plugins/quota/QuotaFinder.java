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
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

public class QuotaFinder {
  private final ProjectCache projectCache;

  @Inject
  QuotaFinder(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public QuotaSection firstMatching(Project.NameKey project) {
    Config cfg = projectCache.getAllProjects().getConfig("quota.config").get();
    String namespace = firstNamespace(cfg, project);
    if (namespace != null) {
      return new QuotaSection(cfg, namespace);
    }
    return null;
  }

  public String firstNamespace(Project.NameKey project) {
    return firstNamespace(projectCache.getAllProjects().getConfig("quota.config").get(), project);
  }

  private String firstNamespace(Config cfg, Project.NameKey project) {
    Set<String> namespaces = cfg.getSubsections(QuotaSection.QUOTA);
    String p = project.get();
    for (String n : namespaces) {
      if ("?/*".equals(n) || n.endsWith("/?/*")) {
        String prefix = n.substring(0, n.length() - 3);
        Matcher m = Pattern.compile("^" + prefix + "([^/]+)/.*$").matcher(p);
        if (m.matches()) {
          return n;
        }
      } else if (n.endsWith("/*")) {
        if (p.startsWith(n.substring(0, n.length() - 1))) {
          return n;
        }
      } else if (n.startsWith("^")) {
        if (p.matches(n.substring(1))) {
          return n;
        }
      } else if (p.equals(n)) {
        return n;
      }
    }
    return null;
  }

  public List<QuotaSection> all() {
    Config cfg = projectCache.getAllProjects().getConfig("quota.config").get();
    List<QuotaSection> sections = new ArrayList<>();
    for (String namespace : cfg.getSubsections(QuotaSection.QUOTA)) {
      sections.add(new QuotaSection(cfg, namespace));
    }
    return sections;
  }
}
