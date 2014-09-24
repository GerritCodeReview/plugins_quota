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

package com.googlesource.gerrit.plugins.quota.usage;

import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class Publisher implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(Publisher.class);

  private final Iterable<UsageDataPublishedListener> listeners;
  private final DynamicSet<UsageDataEventCreator> creators;

  @Inject
  public Publisher(
      DynamicSet<UsageDataPublishedListener> listeners,
      DynamicSet<UsageDataEventCreator> creators) {
    this.listeners = listeners;
    this.creators = creators;
  }

  @Override
  public void run() {
    if(!listeners.iterator().hasNext()) {
      return;
    }

    List<UsageDataPublishedListener.Event> events = new ArrayList<UsageDataPublishedListener.Event>(3);
    for (UsageDataEventCreator creator : creators) {
      events.add(creator.create());
    }

    for (UsageDataPublishedListener l : listeners) {
      try {
        for (Event event : events) {
          l.onUsageDataPublished(event);
        }
      } catch (RuntimeException e) {
        log.warn("Failure in UsageDataPublishedListener", e);
      }
    }
  }

}
