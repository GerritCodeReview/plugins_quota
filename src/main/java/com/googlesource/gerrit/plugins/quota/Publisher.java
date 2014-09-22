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

import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Data;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class Publisher implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(Publisher.class);
  private static final MetaData REPO_SIZE = new MetaDataImpl("repoSize",
      "total file size of the repository", "byte", "B");

  private static final MetaData PUSH_COUNT = new MetaDataImpl("pushCount",
      "number of pushes to the repository since the last event", "", "");

  private static final MetaData FETCH_COUNT = new MetaDataImpl("fetchCount",
      "number of fetches from the repository since the last event", "", "");

  private final Iterable<UsageDataPublishedListener> listeners;
  private final ProjectCache projectCache;
  private final RepoSizeCache repoSizeCache;
  private final PersistentCounter fetchCounts;
  private final PersistentCounter pushCounts;

  @Inject
  public Publisher(
      DynamicSet<UsageDataPublishedListener> listeners,
      ProjectCache projectCache,
      RepoSizeCache repoSizeCache,
      @Named(PersistentCounter.FETCH) PersistentCounter fetchCounts,
      @Named(PersistentCounter.PUSH) PersistentCounter pushCounts) {
    this.listeners = listeners;
    this.projectCache = projectCache;
    this.repoSizeCache = repoSizeCache;
    this.fetchCounts = fetchCounts;
    this.pushCounts = pushCounts;
  }

  @Override
  public void run() {
    if(!listeners.iterator().hasNext()) {
      return;
    }

    UsageDataEvent repoSizeEvent = createRepoSizeEvent();
    UsageDataEvent fetchCountEvent = createEvent(FETCH_COUNT, fetchCounts);
    UsageDataEvent pushCountEvent = createEvent(PUSH_COUNT, pushCounts);
    for (UsageDataPublishedListener l : listeners) {
      try {
        l.onUsageDataPublished(repoSizeEvent);
        l.onUsageDataPublished(pushCountEvent);
        l.onUsageDataPublished(fetchCountEvent);
      } catch (RuntimeException e) {
        log.warn("Failure in UsageDataPublishedListener", e);
      }
    }
  }

  private UsageDataEvent createRepoSizeEvent() {
    UsageDataEvent event = new UsageDataEvent(REPO_SIZE);
    for (Project.NameKey p : projectCache.all()) {
      long size = repoSizeCache.get(p);
      if (size > 0) {
        event.addData(size, p.get());
      }
    }
    return event;
  }

  private UsageDataEvent createEvent(MetaData metaData, PersistentCounter counts) {
    UsageDataEvent event = new UsageDataEvent(metaData);
    for (Project.NameKey p : projectCache.all()) {
      long currentCount = counts.getAndReset(p);
      if (currentCount != 0) {
        event.addData(currentCount, p.get());
      }
    }
    return event;
  }

  private static class UsageDataEvent implements Event {

    private final Timestamp timestamp;
    private final MetaData metaData;
    private final List<Data> data;

    public UsageDataEvent(MetaData metaData) {
      this.metaData = metaData;
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
      return metaData;
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
