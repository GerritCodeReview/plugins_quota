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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.events.UsageDataPublishedListener.Data;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FetchAndPushEventCreatorTest {

  private static final MetaData META = FetchAndPushEventCreator.FETCH_COUNT;

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private File tmp;
  private Project.NameKey p1 = new Project.NameKey("p1");
  private Project.NameKey p2 = new Project.NameKey("p2");
  private Project.NameKey p3 = new Project.NameKey("p3");
  private ProjectCache projectCache;
  private PersistentCounter counts;
  private FetchAndPushEventCreator classUnderTest;

  @Before
  public void setup() throws IOException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();
    projectCache = createMock(ProjectCache.class);
    Iterable<Project.NameKey> projects = Arrays.asList(p1, p2, p3);
    expect(projectCache.all()).andStubReturn(projects);
    replay(projectCache);
    counts = new PersistentCounter(tmp, "test");
    classUnderTest = new FetchAndPushEventCreator(projectCache, counts,
        META);
  }

  @Test
  public void testEmpty() throws Exception {
    Event event = classUnderTest.create();

    assertTrue(event.getData().isEmpty());
    assertSame(META, event.getMetaData());
  }

  @Test
  public void testOneDataPoint() throws Exception {
    counts.increment(p1);

    Event event = classUnderTest.create();

    List<Data> data = event.getData();
    assertEquals(1, data.size());

    checkDataPoint(data.get(0), "p1", 1l);
  }

  private void checkDataPoint(Data dataPoint, String project, long count) {
    assertEquals(project, dataPoint.getProjectName());
    assertEquals(count, dataPoint.getValue());
  }

  @Test
  public void testTwoDataPoints() throws Exception {
    counts.increment(p1);
    counts.increment(p2);
    counts.increment(p2);

    Event event = classUnderTest.create();

    List<Data> data = event.getData();
    assertEquals(2, data.size());

    checkDataPoint(data.get(0), "p1", 1l);
    checkDataPoint(data.get(1), "p2", 2l);
  }

  @After
  public void teardown() {
    counts.close();
    for (File file : tmp.listFiles()) {
      file.delete();
    }
    tmp.delete();
  }

}
