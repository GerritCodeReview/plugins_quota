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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Singleton
public class ProjectNameResolver {

  private static final Logger log = LoggerFactory
      .getLogger(ProjectNameResolver.class);
  private final Path basePath;

  @Inject
  ProjectNameResolver(SitePaths site, @GerritServerConfig final Config cfg) {
    this.basePath =
        site.resolve(cfg.getString("gerrit", null, "basePath")).toPath();
  }

  public Project.NameKey projectName(Repository repo) {
    Path gitDir = repo.getDirectory().toPath();
    if (gitDir.startsWith(basePath)) {
      String p = basePath.relativize(gitDir).toString();
      String n = p.substring(0, p.length() - ".git".length());
      return new Project.NameKey(n);
    } else {
      log.warn("Couldn't determine the project name from " + gitDir);
      return null;
    }
  }
}
