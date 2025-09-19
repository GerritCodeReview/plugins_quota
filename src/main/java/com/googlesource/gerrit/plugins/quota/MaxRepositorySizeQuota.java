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

import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;
import static com.google.gerrit.server.quota.QuotaResponse.error;
import static com.google.gerrit.server.quota.QuotaResponse.noOp;
import static com.google.gerrit.server.quota.QuotaResponse.ok;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.quota.QuotaEnforcer;
import com.google.gerrit.server.quota.QuotaRequestContext;
import com.google.gerrit.server.quota.QuotaResponse;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.mutable.MutableLong;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MaxRepositorySizeQuota implements QuotaEnforcer, RepoSizeCache {
  private static final Logger log = LoggerFactory.getLogger(MaxRepositorySizeQuota.class);

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

  @Inject
  protected MaxRepositorySizeQuota(
      QuotaFinder quotaFinder,
      @Named(REPO_SIZE_CACHE) LoadingCache<Project.NameKey, AtomicLong> cache,
      ProjectCache projectCache) {
    this.quotaFinder = quotaFinder;
    this.cache = cache;
    this.projectCache = projectCache;
  }

  protected Optional<Long> getMaxPackSize(Project.NameKey project) {
    return getMaxPackSize(project, true);
  }

  protected Optional<Long> getMaxPackSize(
      Project.NameKey project, boolean requireProjectExistence) {
    List<Long> maxPackCandidates = new ArrayList<>();
    getMaxPackSize(quotaFinder.firstMatching(project), project, requireProjectExistence)
        .ifPresent(maxPackCandidates::add);
    getMaxPackSize(quotaFinder.getGlobalNamespacedQuota(), project, requireProjectExistence)
        .ifPresent(maxPackCandidates::add);

    return maxPackCandidates.isEmpty()
        ? Optional.empty()
        : Optional.of(Collections.min(maxPackCandidates));
  }

  protected Optional<Long> getMaxPackSize(
      QuotaSection quotaSection, Project.NameKey project, boolean requireProjectExistence) {
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
        long currentSize =
            requireProjectExistence
                ? currentSizeStrict(project)
                : currentSizeOrZeroIfMissing(project);
        maxPackSize1 = Math.max(0, maxRepoSize - currentSize);
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
      log.warn("Couldn't calculate maxPackSize for {}", project, e);
      return Optional.empty();
    }
  }

  /**
   * Returns the current repository size for the given project, in bytes, retrieved from the size
   * cache.
   *
   * <p>This method enforces strict loading semantics: if the size cannot be computed or retrieved,
   * it will propagate the underlying failure via {@link ExecutionException} or {@link
   * UncheckedExecutionException}.
   */
  private long currentSizeStrict(Project.NameKey project)
      throws ExecutionException, UncheckedExecutionException {
    return cache.get(project).get();
  }

  /**
   * Returns the current repository size, or 0 if the repository is missing. Other failures are
   * propagated.
   */
  private long currentSizeOrZeroIfMissing(Project.NameKey project)
      throws ExecutionException, UncheckedExecutionException {
    try {
      return currentSizeStrict(project);
    } catch (ExecutionException | UncheckedExecutionException e) {
      boolean missing =
          Throwables.getCausalChain(e).stream()
              .anyMatch(t -> t instanceof RepositoryNotFoundException);
      if (!missing) {
        throw e;
      }
      return 0L;
    }
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
      log.warn("Error creating RepoSizeEvent", e);
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
      log.warn("Error setting the size of project {}", p, e);
    }
  }

  @Override
  public QuotaResponse dryRun(String quotaGroup, QuotaRequestContext ctx, long numTokens) {
    if (!REPOSITORY_SIZE_GROUP.equals(quotaGroup)) {
      return noOp();
    }

    return ctx.project()
        .flatMap(p -> getMaxPackSize(p, false))
        .map(v -> requestQuota(ctx, numTokens, v, false))
        .orElse(noOp());
  }

  @Override
  public void refill(String quotaGroup, QuotaRequestContext ctx, long numTokens) {
    if (!REPOSITORY_SIZE_GROUP.equals(quotaGroup)) {
      return;
    }

    ctx.project()
        .ifPresent(
            p -> {
              try {
                cache.get(p).getAndUpdate(current -> current > numTokens ? current - numTokens : 0);
              } catch (ExecutionException e) {
                log.warn("Refilling [{}] bytes for repository {} failed", numTokens, p, e);
              }
            });
  }

  @Override
  public QuotaResponse requestTokens(String quotaGroup, QuotaRequestContext ctx, long numTokens) {
    if (!REPOSITORY_SIZE_GROUP.equals(quotaGroup)) {
      return noOp();
    }

    return ctx.project()
        .flatMap(p -> getMaxPackSize(p))
        .map(v -> requestQuota(ctx, numTokens, v, true))
        .orElse(noOp());
  }

  @Override
  public QuotaResponse availableTokens(String quotaGroup, QuotaRequestContext ctx) {
    if (!REPOSITORY_SIZE_GROUP.equals(quotaGroup)) {
      return noOp();
    }
    return ctx.project().flatMap(p -> getMaxPackSize(p)).map(v -> ok(v)).orElse(noOp());
  }

  private QuotaResponse requestQuota(
      QuotaRequestContext ctx, long requested, Long availableSpace, boolean deduct) {
    Project.NameKey r = ctx.project().get();
    if (availableSpace >= requested) {
      if (deduct) {
        try {
          cache.get(r).getAndAdd(requested);
        } catch (ExecutionException e) {
          String msg = String.format("Quota request [%d] failed for repository %s", requested, r);
          log.warn(msg, e);
          return error(msg);
        }
      }
      return ok();
    }

    return error(
        String.format(
            "Requested space [%d] is bigger then available [%d] for repository %s",
            requested, availableSpace, r));
  }
}
