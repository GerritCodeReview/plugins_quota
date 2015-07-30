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

import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.Inject;

import java.util.Properties;

@Singleton
public class GCListener implements GarbageCollectorListener {

  private final RepoSizeCache repoSizeCache;

  @Inject
  public GCListener(RepoSizeCache repoSizeCache) {
    this.repoSizeCache = repoSizeCache;
  }

  @Override
  public void onGarbageCollected(GarbageCollectorListener.Event event) {
    Project.NameKey key = new NameKey(event.getProjectName());
    Properties statistics = event.getStatistics();
    if (statistics != null) {
      Number sizeOfLooseObjects = (Number) statistics.get("sizeOfLooseObjects");
      Number sizeOfPackedObjects =
          (Number) statistics.get("sizeOfPackedObjects");
      if (sizeOfLooseObjects != null && sizeOfPackedObjects != null) {
        repoSizeCache.set(key, sizeOfLooseObjects.longValue()
            + sizeOfPackedObjects.longValue());
        return;
      }
    }
    repoSizeCache.evict(key);
  }
}
