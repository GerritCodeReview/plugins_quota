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
import com.google.common.collect.Ordering;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.apache.commons.lang.mutable.MutableLong;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

@Singleton
class MaxRepositorySizeQuota implements ReceivePackInitializer, PostReceiveHook, RepoSizeCache {
  private static final Logger log = LoggerFactory
      .getLogger(MaxRepositorySizeQuota.class);

  static final String REPO_SIZE_CACHE = "repo_size";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(REPO_SIZE_CACHE, Project.NameKey.class, AtomicLong.class)
            .loader(Loader.class)
            .expireAfterWrite(1, TimeUnit.DAYS);
        bind(RepoSizeCache.class).to(MaxRepositorySizeQuota.class);
      }
    };
  }

  private final QuotaFinder quotaFinder;
  private final LoadingCache<Project.NameKey, AtomicLong> cache;
  private final ProjectCache projectCache;
  private final ProjectNameResolver projectNameResolver;
  private final boolean enableDiskQuota;

  @Inject
  MaxRepositorySizeQuota(QuotaFinder quotaFinder,
      @Named(REPO_SIZE_CACHE) LoadingCache<Project.NameKey, AtomicLong> cache,
      ProjectCache projectCache, ProjectNameResolver projectNameResolver,
      PluginConfigFactory cfg, @PluginName String pluginName) {
    this.quotaFinder = quotaFinder;
    this.cache = cache;
    this.projectCache = projectCache;
    this.projectNameResolver = projectNameResolver;
    this.enableDiskQuota = cfg.getFromGerritConfig(pluginName)
        .getBoolean("enableDiskQuota", true);
  }

  @Override
  public void init(Project.NameKey project, ReceivePack rp) {
    QuotaSection quotaSection = quotaFinder.firstMatching(project);
    Long maxPackSize;
    if (quotaSection == null) {
      return;
    }

    if (!enableDiskQuota) {
      maxPackSize = Long.MAX_VALUE;
      rp.setMaxPackSizeLimit(maxPackSize);
      return;
    }

    Long maxRepoSize = quotaSection.getMaxRepoSize();
    Long maxTotalSize = quotaSection.getMaxTotalSize();
    if (maxRepoSize == null && maxTotalSize == null) {
      return;
    }

    try {
      Long maxPackSize1 = null;
      if (maxRepoSize != null) {
        maxPackSize1 = Math.max(0, maxRepoSize - cache.get(project).get());
      }

      Long maxPackSize2 = null;
      if (maxTotalSize != null) {
        long totalSize = 0;
        for (Project.NameKey p : projectCache.all()) {
          if (quotaSection.matches(p)) {
            totalSize += cache.get(p).get();
          }
        }
        maxPackSize2 = Math.max(0, maxTotalSize - totalSize);
      }

      maxPackSize = Ordering.<Long> natural().nullsLast().min(
          maxPackSize1, maxPackSize2);
      rp.setMaxPackSizeLimit(maxPackSize);
    } catch (ExecutionException e) {
      log.warn("Couldn't setMaxPackSizeLimit on receive-pack for "
          + project.get(), e);
    }
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    Project.NameKey project = projectNameResolver.projectName(rp.getRepository());
    if (!enableDiskQuota) {
      return;
    }
    if (needPack(commands)) {
      try {
        cache.get(project).getAndAdd(rp.getPackSize());
      } catch (ExecutionException e) {
        log.warn("Couldn't process onPostReceive for " + project.get(), e);
      }
    }
  }

  private boolean needPack(Collection<ReceiveCommand> commands) {
    for (ReceiveCommand cmd : commands) {
      if (cmd.getType() != ReceiveCommand.Type.DELETE)
        return true;
    }
    return false;
  }

  @Singleton
  static class Loader extends CacheLoader<Project.NameKey, AtomicLong> {

    private final GitRepositoryManager gitManager;
    private final boolean useGitObjectCount;
    private final boolean enableDiskQuota;

    @Inject
    Loader(GitRepositoryManager gitManager,
        PluginConfigFactory cfg,
        @PluginName String pluginName) {
      this.gitManager = gitManager;
      this.useGitObjectCount = cfg.getFromGerritConfig(pluginName)
          .getBoolean("useGitObjectCount", false);
      this.enableDiskQuota = cfg.getFromGerritConfig(pluginName)
          .getBoolean("enableDiskQuota", true);
    }

    @Override
    public AtomicLong load(Project.NameKey project) throws IOException {
      if(!enableDiskQuota) {
        return new AtomicLong(-1);
      }
      try (Repository git = gitManager.openRepository(project)) {
        if (useGitObjectCount) {
          return new AtomicLong(getDiskUsageByGitObjectCount(git));
        }
        return new AtomicLong(getDiskUsage(git.getDirectory()));
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

    private long getDiskUsageByGitObjectCount(Repository repo)
        throws IOException {
      RepoStatistics stats = new GC((FileRepository) repo).getStatistics();
      return stats.sizeOfLooseObjects + stats.sizeOfPackedObjects;
    }
  }

  @Override
  public long get(Project.NameKey p) {
    try {
      if (enableDiskQuota) {
        return cache.get(p).get();
      }
      return -1;
    } catch (ExecutionException e) {
      log.warn("Error creating RepoSizeEvent", e);
      return 0;
    }
  }

  @Override
  public void evict(Project.NameKey p) {
    if (enableDiskQuota) {
      cache.invalidate(p);
    }
  }

  @Override
  public void set(Project.NameKey p, long size) {
    try {
      if (enableDiskQuota) {
        cache.get(p).set(size);
      }
      else {
        return;
      }
    } catch (ExecutionException e) {
      log.warn("Error setting the size of project " + p.get(), e);
    }
  }
}
