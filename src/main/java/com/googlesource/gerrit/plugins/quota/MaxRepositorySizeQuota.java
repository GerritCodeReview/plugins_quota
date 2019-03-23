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
import com.google.common.flogger.FluentLogger;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang.mutable.MutableLong;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

@Singleton
public class MaxRepositorySizeQuota
    implements ReceivePackInitializer, PostReceiveHook, RepoSizeCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String REPO_SIZE_CACHE = "repo_size";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(REPO_SIZE_CACHE, Project.NameKey.class, AtomicLong.class)
            .loader(Loader.class)
            .expireAfterWrite(Duration.ofDays(1));
        bind(RepoSizeCache.class).to(MaxRepositorySizeQuota.class);
      }
    };
  }

  protected final LoadingCache<Project.NameKey, AtomicLong> cache;
  private final QuotaFinder quotaFinder;
  private final ProjectCache projectCache;
  private final ProjectNameResolver projectNameResolver;

  @Inject
  protected MaxRepositorySizeQuota(
      QuotaFinder quotaFinder,
      @Named(REPO_SIZE_CACHE) LoadingCache<Project.NameKey, AtomicLong> cache,
      ProjectCache projectCache,
      ProjectNameResolver projectNameResolver) {
    this.quotaFinder = quotaFinder;
    this.cache = cache;
    this.projectCache = projectCache;
    this.projectNameResolver = projectNameResolver;
  }

  @Override
  public void init(Project.NameKey project, ReceivePack rp) {
    Optional<Long> maxPackSize = getMaxPackSize(project);
    if (maxPackSize.isPresent()) {
      rp.setMaxPackSizeLimit(maxPackSize.get());
    }
  }

  protected Optional<Long> getMaxPackSize(Project.NameKey project) {
    QuotaSection quotaSection = quotaFinder.firstMatching(project);
    if (quotaSection == null) {
      return Optional.empty();
    }

    Long maxRepoSize = quotaSection.getMaxRepoSize();
    Long maxTotalSize = quotaSection.getMaxTotalSize();
    if (maxRepoSize == null && maxTotalSize == null) {
      return Optional.empty();
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

      return Optional.ofNullable(
          Ordering.<Long>natural().nullsLast().min(maxPackSize1, maxPackSize2));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Couldn't calculate maxPackSize for %s", project.get());
      return Optional.empty();
    }
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    Project.NameKey project = projectNameResolver.projectName(rp.getRepository());
    if (needPack(commands)) {
      try {
        cache.get(project).getAndAdd(rp.getPackSize());
      } catch (ExecutionException e) {
        logger.atWarning().withCause(e).log("Couldn't process onPostReceive for %s", project.get());
      }
    }
  }

  private boolean needPack(Collection<ReceiveCommand> commands) {
    for (ReceiveCommand cmd : commands) {
      if (cmd.getType() != ReceiveCommand.Type.DELETE) {
        return true;
      }
    }
    return false;
  }

  @Singleton
  static class Loader extends CacheLoader<Project.NameKey, AtomicLong> {

    private final GitRepositoryManager gitManager;
    private final boolean useGitObjectCount;

    @Inject
    Loader(
        GitRepositoryManager gitManager, PluginConfigFactory cfg, @PluginName String pluginName) {
      this.gitManager = gitManager;
      this.useGitObjectCount =
          cfg.getFromGerritConfig(pluginName).getBoolean("useGitObjectCount", false);
    }

    @Override
    public AtomicLong load(Project.NameKey project) throws IOException {
      try (Repository git = gitManager.openRepository(project)) {
        if (useGitObjectCount) {
          return new AtomicLong(getDiskUsageByGitObjectCount(git));
        }
        return new AtomicLong(getDiskUsage(git.getDirectory()));
      }
    }

    private static long getDiskUsage(File dir) throws IOException {
      final MutableLong size = new MutableLong();
      Files.walkFileTree(
          dir.toPath(),
          new SimpleFileVisitor<Path>() {
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

    private long getDiskUsageByGitObjectCount(Repository repo) throws IOException {
      RepoStatistics stats = new GC((FileRepository) repo).getStatistics();
      return stats.sizeOfLooseObjects + stats.sizeOfPackedObjects;
    }
  }

  @Override
  public long get(Project.NameKey p) {
    try {
      return cache.get(p).get();
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Error creating RepoSizeEvent");
      return 0;
    }
  }

  @Override
  public void evict(Project.NameKey p) {
    cache.invalidate(p);
  }

  @Override
  public void set(Project.NameKey p, long size) {
    try {
      cache.get(p).set(size);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Error setting the size of project %s", project.get());
    }
  }
}
