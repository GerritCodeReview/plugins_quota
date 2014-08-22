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

import static com.googlesource.gerrit.plugins.quota.MaxRepositorySizeQuota.REPO_SIZE_CACHE;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GCListener implements GarbageCollectorListener {

  private final LoadingCache<NameKey, AtomicLong> repoSizeCache;

  public GCListener(ProjectCache projectCache,
      @Named(REPO_SIZE_CACHE) LoadingCache<Project.NameKey, AtomicLong> repoSizeCache) {
        this.repoSizeCache = repoSizeCache;
  }

  @Override
  public void onGarbageCollected(GarbageCollectorListener.Event event) {
    Project.NameKey key = new NameKey(event.getProjectName());
    repoSizeCache.invalidate(key);
  }
}
