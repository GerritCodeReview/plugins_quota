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

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import com.googlesource.gerrit.plugins.quota.usage.UsageDataEventCreator;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;

import java.io.File;

public class CountModule extends AbstractModule {
  public static final String FETCH = "FETCH_COUNTS";
  public static final String PUSH = "PUSH_COUNTS";

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), UsageDataEventCreator.class).to(creatorKey(FETCH));
    DynamicSet.bind(binder(), UsageDataEventCreator.class).to(creatorKey(PUSH));
    DynamicSet.bind(binder(), PostReceiveHook.class).to(
        FetchAndPushListener.class);
    DynamicSet.bind(binder(), PreUploadHook.class).to(
        FetchAndPushListener.class);

  }

  private static Key<UsageDataEventCreator> creatorKey(String kind) {
    Key<UsageDataEventCreator> pushCreatorKey =
        Key.get(new TypeLiteral<UsageDataEventCreator>() {},
            Names.named(kind));
    return pushCreatorKey;
  }

  @Provides
  @Singleton
  @Named(FETCH)
  PersistentCounter provideFetchCounter(@PluginData File dataDir) {
    return new PersistentCounter(dataDir, "fetch");
  }

  @Provides
  @Singleton
  @Named(PUSH)
  PersistentCounter providePushCounter(@PluginData File dataDir) {
    return new PersistentCounter(dataDir, "push");
  }

  @Provides
  @Singleton
  @Named(FETCH)
  UsageDataEventCreator provideFetchEventCreator(ProjectCache projectCache,
      @Named(FETCH) PersistentCounter fetchCounts) {
    return new FetchAndPushEventCreator(projectCache, fetchCounts,
        FetchAndPushEventCreator.FETCH_COUNT);
  }

  @Provides
  @Singleton
  @Named(PUSH)
  UsageDataEventCreator providePushEventCreator(ProjectCache projectCache,
      @Named(PUSH) PersistentCounter fetchCounts) {
    return new FetchAndPushEventCreator(projectCache, fetchCounts,
        FetchAndPushEventCreator.PUSH_COUNT);
  }

}
