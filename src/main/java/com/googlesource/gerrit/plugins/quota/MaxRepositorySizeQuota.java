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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.mutable.MutableLong;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class MaxRepositorySizeQuota implements ReceivePackInitializer, PostReceiveHook {
  private static final Logger log = LoggerFactory
      .getLogger(MaxRepositorySizeQuota.class);

  private static final String CACHE_NAME = "repo_size";

  static Module module() {
    return new CacheModule() {
      protected void configure() {
        persist(CACHE_NAME, Project.NameKey.class, AtomicLong.class)
            .loader(Loader.class)
            .expireAfterWrite(1, TimeUnit.DAYS);
      }
    };
  }

  private final QuotaFinder quotaFinder;
  private final LoadingCache<Project.NameKey, AtomicLong> cache;
  private final SitePaths site;
  private final Path basePath;

  @Inject
  MaxRepositorySizeQuota(QuotaFinder quotaFinder,
      @Named(CACHE_NAME) LoadingCache<Project.NameKey, AtomicLong> cache,
      SitePaths site, @GerritServerConfig final Config cfg) {
    this.quotaFinder = quotaFinder;
    this.cache = cache;
    this.site = site;
    basePath = site.resolve(cfg.getString("gerrit", null, "basePath")).toPath();
  }

  @Override
  public void init(Project.NameKey project, ReceivePack rp) {
    QuotaSection quotaSection = quotaFinder.firstMatching(project);
    if (quotaSection == null) {
      return;
    }

    Long maxRepoSize = quotaSection.getMaxRepoSize();
    if (maxRepoSize == null) {
      return;
    }

    try {
      long maxPackSize = Math.max(0, maxRepoSize - cache.get(project).get());
      rp.setMaxPackSizeLimit(maxPackSize);
    } catch (ExecutionException e) {
      log.warn("Couldn't setMaxPackSizeLimit on receive-pack for "
          + project.get(), e);
    }
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    Project.NameKey project = projectName(rp);
    try {
      cache.get(project).getAndAdd(rp.getPackSize());
    } catch (ExecutionException e) {
      log.warn("Couldn't process onPostReceive for " + project.get(), e);
    }
  }

  private Project.NameKey projectName(ReceivePack rp) {
    Path gitDir = rp.getRepository().getDirectory().toPath();
    if (gitDir.startsWith(basePath)) {
      String p = basePath.relativize(gitDir).toString();
      String n = p.substring(0, p.length() - ".git".length());
      return new Project.NameKey(n);
    } else {
      log.warn("Couldn't determine the project name from " + gitDir);
      return null;
    }
  }

  @Singleton
  static class Loader extends CacheLoader<Project.NameKey, AtomicLong> {

    private final GitRepositoryManager gitManager;

    @Inject
    Loader(GitRepositoryManager gitManager) {
      this.gitManager = gitManager;
    }

    @Override
    public AtomicLong load(Project.NameKey project) throws IOException {
      Repository git = gitManager.openRepository(project);
      try {
        return new AtomicLong(getDiskUsage(git.getDirectory()));
      } finally {
        git.close();
      }
    }

    private static long getDiskUsage(File dir) throws IOException {
      final MutableLong size = new MutableLong();
      Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
            throws IOException {
          if (attrs.isRegularFile()) {
            size.add(attrs.size());
          }
          return FileVisitResult.CONTINUE;
        }
      });
      return size.longValue();
    }
  }
}
