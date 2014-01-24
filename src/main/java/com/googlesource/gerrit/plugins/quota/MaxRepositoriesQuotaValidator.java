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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.Set;

public class MaxRepositoriesQuotaValidator implements
    ProjectCreationValidationListener {
  private static final String QUOTA = "quota";
  private static final String KEY_MAX_REPOS = "maxRepos";

  private final ProjectCache projectCache;

  @Inject
  MaxRepositoriesQuotaValidator(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  @Override
  public void validateNewProject(CreateProjectArgs args) throws ValidationException {
    Config cfg = projectCache.getAllProjects().getConfig("quota.config").get();
    Set<String> namespaces = cfg.getSubsections(QUOTA);
    for (String n : namespaces) {
      Namespace namespace = new Namespace(n, args.getProject());
      if (namespace.applies()) {
        if (cfg.getNames(QUOTA, n).contains(KEY_MAX_REPOS)) {
          int maxRepos = cfg.getInt(QUOTA, n, KEY_MAX_REPOS, Integer.MAX_VALUE);
          int count = 0;
          for (Project.NameKey p : projectCache.all()) {
            if (namespace.matches(p)) {
              count++;
            }
          }
          if (count >= maxRepos) {
            StringBuilder msg = new StringBuilder();
            msg.append("Project cannot be created because a quota for the namespace '");
            msg.append(namespace.get());
            msg.append("' allows at most ");
            msg.append(maxRepos);
            msg.append(" projects and ");
            msg.append(count);
            msg.append(" projects already exist.");
            throw new ValidationException(msg.toString());
          }
        }
        break;
      }
    }
  }
}
