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

  private Iterable<UsageDataPublishedListener> listeners;
  private ProjectCache projectCache;
  private LoadingCache<NameKey, AtomicLong> repoSizeCache;

  @Inject
  public Publisher(DynamicSet<UsageDataPublishedListener> listeners, ProjectCache projectCache,
      @Named(MaxRepositorySizeQuota.REPO_SIZE_CACHE) LoadingCache<Project.NameKey, AtomicLong> repoSizeCache) {
    this.listeners = listeners;
    this.projectCache = projectCache;
    this.repoSizeCache = repoSizeCache;
  }

  @Override
  public void run() {
    if(!listeners.iterator().hasNext()) {
      return;
    }

    try {
      RepoSizeEvent event = createEvent();
      for (UsageDataPublishedListener l : listeners) {
          try {
            l.onUsageDataPublished(event);
          } catch (RuntimeException e) {
            log.warn("Failure in UsageDataPublishedListener", e);
          }
      }
    } catch (ExecutionException e) {
      log.warn("Error creating RepoSizeEvent", e);
    }
  }

  private RepoSizeEvent createEvent() throws ExecutionException {
    RepoSizeEvent event = new RepoSizeEvent();
    for (Project.NameKey p : projectCache.all()) {
        long size = repoSizeCache.get(p).get();
        event.addData(size, p.get());
    }
    return event;
  }

  private static class RepoSizeEvent implements Event {

    private final Timestamp timestamp;
    private final List<Data> data;

    private RepoSizeEvent() {
      timestamp = new Timestamp(System.currentTimeMillis());
      data = new ArrayList<Data>();
    }

    private void addData(final long value, final String projectName) {
      Data dataRow = new Data() {

        @Override
        public long getValue() {
          return value;
        }

        @Override
        public String getProjectName() {
          return projectName;
        }
      };

      data.add(dataRow);
    }

    @Override
    public MetaData getMetaData() {
      return REPO_SIZE;
    }

    @Override
    public Timestamp getInstant() {
      return timestamp  ;
    }

    @Override
    public List<Data> getData() {
      return data;
    }
  };
}
