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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

public class DeletionListenerTest {
  protected static final String MY_PROJECT = "my-project";

  @Test
  public void testName() throws Exception {
    RepoSizeCache repoSizeCache = createMock(RepoSizeCache.class);
    Project.NameKey p = new Project.NameKey(MY_PROJECT);
    repoSizeCache.evict(p);
    DeletionListener classUnderTest = new DeletionListener(repoSizeCache);
    replay(repoSizeCache);

    ProjectDeletedListener.Event event =
        new ProjectDeletedListener.Event() {
          @Override
          public String getProjectName() {
            return MY_PROJECT;
          }

          @Override
          public NotifyHandling getNotify() {
            return NotifyHandling.ALL;
          }
        };
    classUnderTest.onProjectDeleted(event);

    verify(repoSizeCache);
  }
}
