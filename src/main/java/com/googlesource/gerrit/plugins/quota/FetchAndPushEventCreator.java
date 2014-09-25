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

import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.Map.Entry;

@Singleton
public class FetchAndPushEventCreator implements UsageDataEventCreator {
  static final MetaData PUSH_COUNT = new MetaDataImpl("pushCount", "", "",
      "number of pushes to the repository since the last event");

  static final MetaData FETCH_COUNT = new MetaDataImpl("fetchCount", "", "",
      "number of fetches from the repository since the last event");

  private final PersistentCounter counts;
  private final MetaData metaData;

  public FetchAndPushEventCreator(PersistentCounter counts, MetaData metaData) {
    this.counts = counts;
    this.metaData = metaData;
  }

  @Override
  public String getName() {
    return metaData.getName();
  }

  @Override
  public Event create() {
    UsageDataEvent event = new UsageDataEvent(metaData);
    Map<String, Long> values = counts.getAllAndClear();
    for (Entry<String, Long> entry : values.entrySet()) {
      Long value = entry.getValue();
      if (value != 0) {
        event.addData(value, entry.getKey());
      }
    }
    return event;
  }

}
