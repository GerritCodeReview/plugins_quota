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
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
class FetchAndPushCounter implements PostReceiveHook, PreUploadHook {
  private static final Logger log = LoggerFactory
      .getLogger(FetchAndPushCounter.class);

  static final String PUSH_COUNT_CACHE = "push_count";
  static final String FETCH_COUNT_CACHE = "fetch_count";

  static Module module() {
    return new CacheModule() {
      protected void configure() {
        persist(PUSH_COUNT_CACHE, Project.NameKey.class, AtomicLong.class)
            .loader(Loader.class)
            .expireAfterWrite(Integer.MAX_VALUE, TimeUnit.DAYS);
        persist(FETCH_COUNT_CACHE, Project.NameKey.class, AtomicLong.class)
            .loader(Loader.class)
            .expireAfterWrite(Integer.MAX_VALUE, TimeUnit.DAYS);
      }
    };
  }

  private final LoadingCache<Project.NameKey, AtomicLong> pushCountCache;
  private final LoadingCache<Project.NameKey, AtomicLong> fetchCountCache;
  private final Path basePath;

  @Inject
  FetchAndPushCounter(
      @Named(PUSH_COUNT_CACHE) LoadingCache<Project.NameKey, AtomicLong> pushCountCache,
      @Named(FETCH_COUNT_CACHE) LoadingCache<Project.NameKey, AtomicLong> fetchCountCache,
      SitePaths site, @GerritServerConfig final Config cfg) {
    this.pushCountCache = pushCountCache;
    this.fetchCountCache = fetchCountCache;
    this.basePath = site.resolve(cfg.getString("gerrit", null, "basePath")).toPath();
  }

  @Override
  public void onBeginNegotiateRound(UploadPack up,
      Collection<? extends ObjectId> wants, int cntOffered)
      throws ServiceMayNotContinueException {
  }

  @Override
  public void onEndNegotiateRound(UploadPack up,
      Collection<? extends ObjectId> wants, int cntCommon, int cntNotFound,
      boolean ready) throws ServiceMayNotContinueException {
  }

  @Override
  public void onSendPack(UploadPack up, Collection<? extends ObjectId> wants,
      Collection<? extends ObjectId> haves)
      throws ServiceMayNotContinueException {
    Project.NameKey project =
        MaxRepositorySizeQuota.projectName(up.getRepository(), basePath);
    incrementCount(fetchCountCache, project);
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    Project.NameKey project =
        MaxRepositorySizeQuota.projectName(rp.getRepository(), basePath);
    incrementCount(pushCountCache, project);
  }

  private static void incrementCount(LoadingCache<Project.NameKey, AtomicLong> cache,
      Project.NameKey project) {
    try {
      cache.get(project).incrementAndGet();
    } catch (ExecutionException e) {
      log.warn("Couldn't increment usage counter for project " + project.get(), e);
    }
  }

  @Singleton
  static class Loader extends CacheLoader<Project.NameKey, AtomicLong> {

    @Override
    public AtomicLong load(Project.NameKey project) throws IOException {
      return new AtomicLong(0);
    }
  }
}
