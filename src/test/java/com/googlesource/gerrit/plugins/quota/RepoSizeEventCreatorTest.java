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
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.events.UsageDataPublishedListener.Data;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class RepoSizeEventCreatorTest {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private Project.NameKey p1 = new Project.NameKey("p1");
  private Project.NameKey p2 = new Project.NameKey("p2");
  private Project.NameKey p3 = new Project.NameKey("p3");
  private ProjectCache projectCache;
  private RepoSizeEventCreator classUnderTest;
  private File tmp;
  private RepoSizeCache repoSizeCache;

  @Before
  public void setup() throws IOException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();
    projectCache = createMock(ProjectCache.class);
    Iterable<Project.NameKey> projects = Arrays.asList(p1, p2, p3);
    expect(projectCache.all()).andStubReturn(projects);
    repoSizeCache = createNiceMock(RepoSizeCache.class);
    replay(projectCache);
    classUnderTest = new RepoSizeEventCreator(projectCache, repoSizeCache);
  }

  @Test
  public void testEmpty() {
    replay(repoSizeCache);

    Event event = classUnderTest.create();

    assertEquals("repoSize", event.getMetaData().getName());
    assertTrue(event.getData().isEmpty());
  }

  @Test
  public void testOneDataPoint() {
    expect(repoSizeCache.get(p1)).andStubReturn(100L);
    replay(repoSizeCache);

    Event event = classUnderTest.create();

    assertEquals("repoSize", event.getMetaData().getName());
    assertEquals(1, event.getData().size());
    Data dataPoint = event.getData().get(0);
    assertEquals("p1", dataPoint.getProjectName());
    assertEquals(100L, dataPoint.getValue());
  }
}
