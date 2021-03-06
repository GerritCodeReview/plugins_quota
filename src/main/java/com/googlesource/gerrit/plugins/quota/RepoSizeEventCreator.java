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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RepoSizeEventCreator implements UsageDataEventCreator {

  private static final MetaData REPO_SIZE =
      new MetaDataImpl("repoSize", "byte", "B", "total file size of the repository");

  private final ProjectCache projectCache;
  private final RepoSizeCache repoSizeCache;

  @Inject
  public RepoSizeEventCreator(ProjectCache projectCache, RepoSizeCache repoSizeCache) {
    this.projectCache = projectCache;
    this.repoSizeCache = repoSizeCache;
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

  @Override
  public String getName() {
    return REPO_SIZE.getName();
  }

  @Override
  public Event create() {
    return createRepoSizeEvent();
  }
}
