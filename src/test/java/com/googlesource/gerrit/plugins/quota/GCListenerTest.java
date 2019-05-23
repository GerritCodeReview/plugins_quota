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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Properties;
import org.junit.Test;

public class GCListenerTest {
  private static final String PROJECT_NAME = "my-project";

  @Test
  public void testEventWithStatistics() {
    RepoSizeCache repoSizeCache = createMock(RepoSizeCache.class);
    repoSizeCache.set(Project.nameKey(PROJECT_NAME), 9999L);
    expectLastCall();
    replay(repoSizeCache);

    GCListener listener = new GCListener(repoSizeCache);

    final Properties statistics = new Properties();
    statistics.put("sizeOfLooseObjects", 1234L);
    statistics.put("sizeOfPackedObjects", 8765L);

    listener.onGarbageCollected(createEvent(PROJECT_NAME, statistics));

    verify(repoSizeCache);
  }

  @Test
  public void testEventWithoutStatistics() {
    RepoSizeCache repoSizeCache = createMock(RepoSizeCache.class);
    repoSizeCache.evict(Project.nameKey(PROJECT_NAME));
    expectLastCall();
    replay(repoSizeCache);

    GCListener listener = new GCListener(repoSizeCache);

    listener.onGarbageCollected(createEvent(PROJECT_NAME, null));

    verify(repoSizeCache);
  }

  @Test
  public void testEventWithEmptyStatistics() {
    RepoSizeCache repoSizeCache = createMock(RepoSizeCache.class);
    repoSizeCache.evict(Project.nameKey(PROJECT_NAME));
    expectLastCall();
    replay(repoSizeCache);

    GCListener listener = new GCListener(repoSizeCache);

    listener.onGarbageCollected(createEvent(PROJECT_NAME, new Properties()));

    verify(repoSizeCache);
  }

  private static GarbageCollectorListener.Event createEvent(
      final String projectName, final Properties statistics) {
    GarbageCollectorListener.Event event =
        new GarbageCollectorListener.Event() {

          @Override
          public String getProjectName() {
            return projectName;
          }

          @Override
          public Properties getStatistics() {
            return statistics;
          }

          @Override
          public NotifyHandling getNotify() {
            return NotifyHandling.ALL;
          }
        };
    return event;
  }
}
