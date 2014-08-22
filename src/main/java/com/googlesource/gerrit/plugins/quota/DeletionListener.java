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

import static com.googlesource.gerrit.plugins.quota.FetchAndPushCounter.FETCH_COUNTS;
import static com.googlesource.gerrit.plugins.quota.FetchAndPushCounter.PUSH_COUNTS;
import static com.googlesource.gerrit.plugins.quota.MaxRepositorySizeQuota.REPO_SIZE_CACHE;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class DeletionListener implements ProjectDeletedListener {

  private final LoadingCache<NameKey, AtomicLong> repoSizeCache;
  private final ConcurrentMap<NameKey, AtomicLong> pushCounts;
  private final ConcurrentMap<NameKey, AtomicLong> fetchCounts;

  @Inject
  public DeletionListener(ProjectCache projectCache,
      @Named(REPO_SIZE_CACHE) LoadingCache<Project.NameKey, AtomicLong> repoSizeCache,
      @Named(PUSH_COUNTS) ConcurrentMap<Project.NameKey, AtomicLong> pushCounts,
      @Named(FETCH_COUNTS) ConcurrentMap<Project.NameKey, AtomicLong> fetchCounts) {
        this.repoSizeCache = repoSizeCache;
        this.pushCounts = pushCounts;
        this.fetchCounts = fetchCounts;
  }

  @Override
  public void onProjectDeleted(Event event) {
    Project.NameKey p = new NameKey(event.getProjectName());
    repoSizeCache.invalidate(p);
    pushCounts.remove(p);
    fetchCounts.remove(p);
  }
}
