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

package com.googlesource.gerrit.plugins.quota.count;

import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.quota.usage.MetaDataImpl;
import com.googlesource.gerrit.plugins.quota.usage.UsageDataEvent;
import com.googlesource.gerrit.plugins.quota.usage.UsageDataEventCreator;

@Singleton
public class FetchAndPushEventCreator implements UsageDataEventCreator {

  public static final MetaData PUSH_COUNT = new MetaDataImpl("pushCount", "", "",
      "number of pushes to the repository since the last event");

  public static final MetaData FETCH_COUNT = new MetaDataImpl("fetchCount", "", "",
      "number of fetches from the repository since the last event");

  private final ProjectCache projectCache;
  private final PersistentCounter counts;
  private final MetaData metaData;

  public FetchAndPushEventCreator(ProjectCache projectCache, PersistentCounter counts,
      MetaData metaData) {
        this.projectCache = projectCache;
        this.counts = counts;
        this.metaData = metaData;
  }

  @Override
  public Event create() {
    UsageDataEvent event = new UsageDataEvent(metaData);
    for (Project.NameKey p : projectCache.all()) {
      long currentCount = counts.getAndReset(p);
      if (currentCount != 0) {
        event.addData(currentCount, p.get());
      }
    }
    return event;
  }

}
