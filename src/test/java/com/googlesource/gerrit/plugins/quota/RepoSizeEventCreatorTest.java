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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Data;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.server.project.ProjectCache;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class RepoSizeEventCreatorTest {
  private Project.NameKey p1 = Project.nameKey("p1");
  private Project.NameKey p2 = Project.nameKey("p2");
  private Project.NameKey p3 = Project.nameKey("p3");
  private ProjectCache projectCache;
  private RepoSizeEventCreator classUnderTest;
  private File tmp;
  private RepoSizeCache repoSizeCache;

  @Before
  public void setup() throws IOException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();
    projectCache = mock(ProjectCache.class);
    ImmutableSortedSet<Project.NameKey> projects = ImmutableSortedSet.of(p1, p2, p3);
    when(projectCache.all()).thenReturn(projects);
    repoSizeCache = mock(RepoSizeCache.class);
    classUnderTest = new RepoSizeEventCreator(projectCache, repoSizeCache);
  }

  @Test
  public void testEmpty() {
    Event event = classUnderTest.create();

    assertEquals("repoSize", event.getMetaData().getName());
    assertTrue(event.getData().isEmpty());
  }

  @Test
  public void testOneDataPoint() {
    when(repoSizeCache.get(p1)).thenReturn(100L);

    Event event = classUnderTest.create();

    assertEquals("repoSize", event.getMetaData().getName());
    assertEquals(1, event.getData().size());
    Data dataPoint = event.getData().get(0);
    assertEquals("p1", dataPoint.getProjectName());
    assertEquals(100L, dataPoint.getValue());
  }
}
