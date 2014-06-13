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
class ProjectNameResolver {

  private static final Logger log = LoggerFactory
      .getLogger(ProjectNameResolver.class);
  private final Path basePath;

  @Inject
  ProjectNameResolver(SitePaths site, @GerritServerConfig final Config cfg) {
    this.basePath =
        site.resolve(cfg.getString("gerrit", null, "basePath")).toPath();
  }

  Project.NameKey projectName(Repository repo) {
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
