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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.*;

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.googlesource.gerrit.plugins.quota.PersistentCounter.CounterException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class PersistentCounterTest {

  private File tmp;
  private PersistentCounter pushCounts;
  private PersistentCounter fetchCounts;
  private UsageDataEventCreator pushCreator;
  private UsageDataEventCreator fetchCreator;

  @Before
  public void setup() throws IOException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();

    final File dir = tmp;

    final ProjectCache projectCache = createNiceMock(ProjectCache.class);
    replay(projectCache);

    final RepoSizeCache repoSizeCache = createNiceMock(RepoSizeCache.class);
    replay(repoSizeCache);

    Module testModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(RepoSizeCache.class).toInstance(repoSizeCache);
        bind(File.class).annotatedWith(PluginData.class).toInstance(dir);
        bind(ProjectCache.class).toInstance(projectCache);
      }
    };
    Injector injector =
        Guice.createInjector(testModule, PersistentCounter.module());

    pushCounts = getCounter(injector, PersistentCounter.PUSH);
    fetchCounts = getCounter(injector, PersistentCounter.FETCH);

    pushCreator = getCreator(injector, PersistentCounter.PUSH);
    fetchCreator = getCreator(injector, PersistentCounter.FETCH);
  }

  private UsageDataEventCreator getCreator(Injector injector, String kind) {
    Key<UsageDataEventCreator> key =
        Key.get(new TypeLiteral<UsageDataEventCreator>() {}, Names.named(kind));
    return injector.getInstance(key);
  }

  private PersistentCounter getCounter(Injector injector, String kind) {
    Key<PersistentCounter> key =
        Key.get(new TypeLiteral<PersistentCounter>() {}, Names.named(kind));
    return injector.getInstance(key);
  }

  @Test
  public void testCounterWiring() {
    assertNotNull(pushCounts);
    assertNotNull(fetchCounts);
    assertTrue(pushCounts != fetchCounts);
  }

  @Test
  public void testCreatorWiring() {
    assertNotNull(pushCreator);
    assertNotNull(fetchCreator);
    assertTrue(pushCreator != fetchCreator);
  }

  @Test
  public void testEmptyCounter() throws CounterException {
    checkCounting(pushCounts, 0l);
    checkCounting(fetchCounts, 0l);
  }

  @Test
  public void testFirstValue() throws CounterException {
    checkCounting(pushCounts, 1l);
    checkCounting(fetchCounts, 1l);
  }

  @Test
  public void testNextValue() throws CounterException {
    checkCounting(pushCounts, 2l);
    checkCounting(fetchCounts, 2l);
  }

  private void checkCounting(PersistentCounter counter, long count)
      throws CounterException {
    Project.NameKey p = new Project.NameKey("p");
    countTo(counter, p, count);
    Map<String, Long> counts = counter.getAllAndClear();
    if (count > 0) {
      assertEquals(count, counts.get("p").longValue());
    } else {
      assertTrue(counts.isEmpty());
    }
  }

  private void countTo(PersistentCounter counter, Project.NameKey p, long count) {
    Map<String, Long> counts = counter.getAllAndClear();
    assertTrue(counts.isEmpty());
    for (long i = 0l; i < count; i++) {
      counter.increment(p);
    }
  }

  @Test
  public void testKind() {
    assertEquals("push", pushCounts.getKind());
    assertEquals("fetch", fetchCounts.getKind());
  }

  @After
  public void teardown() {
    for (File file : tmp.listFiles()) {
      file.delete();
    }
    tmp.delete();
  }

  @Test
  public void testGetAllIsBlocking() throws Exception {
    /*
     * 1) count to 2
     * 2) concurrently increment counter:
     *   - wait until in between SELECT AND UPDATE
     *   - attempt to increment counter concurrently
     *   - expect that the increment waits until counter is cleared
     *  3) make sure that total count is 3
     */

    final Project.NameKey p = new Project.NameKey("p");
    final CountDownLatch latch = new CountDownLatch(1);
    countTo(pushCounts, p, 2);

    Runnable callback = new Runnable() {
      @Override
      public void run() {
        final Runnable incrementor = new Runnable() {
          @Override
          public void run() {
            pushCounts.increment(p);
            latch.countDown();
          }
        };
        Executors.newSingleThreadExecutor().execute(incrementor);

        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    };

    pushCounts.setCallbackBetweenGetAndClear(callback);

    assertEquals(2, pushCounts.getAllAndClear().get("p").longValue());

    latch.await();
    pushCounts.clearCallbackBetweenGetAndClear();

    assertEquals(1, pushCounts.getAllAndClear().get("p").longValue());
  }
}
