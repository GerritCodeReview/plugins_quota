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


import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.inject.Inject;
import com.google.inject.name.Named;


public class DeletionListener implements ProjectDeletedListener {

  private final RepoSizeCache repoSizeCache;
  private final PersistentCounter pushCounts;
  private final PersistentCounter fetchCounts;

  @Inject
  public DeletionListener(RepoSizeCache repoSizeCache,
      @Named(PersistentCounter.FETCH) PersistentCounter fetchCounts,
      @Named(PersistentCounter.PUSH) PersistentCounter pushCounts) {
        this.repoSizeCache = repoSizeCache;
        this.fetchCounts = fetchCounts;
        this.pushCounts = pushCounts;
  }

  @Override
  public void onProjectDeleted(Event event) {
    Project.NameKey p = new NameKey(event.getProjectName());
    repoSizeCache.remove(p);
    pushCounts.remove(p);
    fetchCounts.remove(p);
  }
}
