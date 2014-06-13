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

import com.google.common.cache.Cache;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Data;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class Publisher implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(Publisher.class);
  private static final MetaData REPO_SIZE = new MetaData() {

    @Override
    public String getName() {
      return "repoSize";
    }

    @Override
    public String getDescription() {
      return "total file size of the repository";
    }

    @Override
    public String getUnitName() {
      return "byte";
    }

    @Override
    public String getUnitSymbol() {
      return "B";
    }
  };

  private static final MetaData PUSH_COUNT = new MetaData() {

    @Override
    public String getName() {
      return "pushCount";
    }

    @Override
    public String getDescription() {
      return "number of pushes to the repository since the last event";
    }

    @Override
    public String getUnitName() {
      return "";
    }

    @Override
    public String getUnitSymbol() {
      return "";
    }
  };

  private static final MetaData FETCH_COUNT = new MetaData() {

    @Override
    public String getName() {
      return "fetchCount";
    }

    @Override
    public String getDescription() {
      return "number of fetches from the repository since the last event";
    }

    @Override
    public String getUnitName() {
      return "";
    }

    @Override
    public String getUnitSymbol() {
      return "";
    }
  };

  private Iterable<UsageDataPublishedListener> listeners;
  private ProjectCache projectCache;
  private LoadingCache<NameKey, AtomicLong> repoSizeCache;
  private Cache<NameKey, AtomicLong> numberOfPushesCache;
  private Cache<NameKey, AtomicLong> numberOfFetchesCache;

  @Inject
  public Publisher(DynamicSet<UsageDataPublishedListener> listeners,
      ProjectCache projectCache,
      @Named(MaxRepositorySizeQuota.REPO_SIZE_CACHE) LoadingCache<Project.NameKey,
      AtomicLong> repoSizeCache,
      @Named(FetchAndPushCounter.PUSH_COUNT_CACHE) Cache<Project.NameKey,
      AtomicLong> pushCountCache,
      @Named(FetchAndPushCounter.FETCH_COUNT_CACHE) Cache<Project.NameKey,
      AtomicLong> fetchCountCache) {
    this.listeners = listeners;
    this.projectCache = projectCache;
    this.repoSizeCache = repoSizeCache;
    this.numberOfPushesCache = pushCountCache;
    this.numberOfFetchesCache = fetchCountCache;
  }

  @Override
  public void run() {

    if(!listeners.iterator().hasNext()) {
      return;
    }
    try {
      RepoEvent repoSizeEvent = createRepoSizeEvent();
      RepoEvent pushCountEvent = createEvent(PUSH_COUNT, numberOfPushesCache);
      RepoEvent fetchCountEvent = createEvent(FETCH_COUNT, numberOfFetchesCache);
      for (UsageDataPublishedListener l : listeners) {
          try {
            l.onUsageDataPublished(repoSizeEvent);
            l.onUsageDataPublished(pushCountEvent);
            l.onUsageDataPublished(fetchCountEvent);
          } catch (RuntimeException e) {
            log.warn("Failure in UsageDataPublishedListener", e);
          }
      }
    } catch (ExecutionException e) {
      log.warn("Error accessing repoSizeCache", e);
    }
  }

  private RepoEvent createRepoSizeEvent() throws ExecutionException {
    RepoEvent event = new RepoEvent(REPO_SIZE);
    for (Project.NameKey p : projectCache.all()) {
        long size = repoSizeCache.get(p).get();
        event.addData(size, p.get());
    }
    return event;
  }

  private RepoEvent createEvent(MetaData pushCount, Cache<NameKey, AtomicLong> cache) {
    RepoEvent event = new RepoEvent(pushCount);
    for (Project.NameKey p : projectCache.all()) {
        AtomicLong ifPresent = cache.getIfPresent(p);
        if (ifPresent != null) {
          event.addData(ifPresent.get(), p.get());
          cache.invalidate(p);
        }
    }
    return event;
  }

  private static class RepoEvent implements Event{

    private final Timestamp timestamp;
    private final MetaData metaData;
    private final List<Data> data = new ArrayList<Data>();

    public RepoEvent(MetaData metaData) {
      this.metaData = metaData;
      timestamp = new Timestamp(System.currentTimeMillis());
    }

    public void addData(final long value, final String projectName) {
      Data dataRow = new Data() {

        @Override
        public long getValue() {
          return value;
        }

        @Override
        public String getProjectName() {
          return projectName;
        }};
        data.add(dataRow);
    }

    @Override
    public MetaData getMetaData() {
      return metaData;
    }

    @Override
    public Timestamp getInstant() {
      return timestamp  ;
    }

    @Override
    public List<Data> getData() {
      return data;
    }};

}
