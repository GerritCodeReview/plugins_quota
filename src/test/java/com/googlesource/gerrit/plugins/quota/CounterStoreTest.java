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

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class CounterStoreTest {

  private File tmp;
  private CounterStore classUnderTest;
  private Map<NameKey, AtomicLong> pushCounts;
  private Map<NameKey, AtomicLong> fetchCounts;

  @Before
  public void setup() throws IOException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();

    final File dir = tmp;
    Module testModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(File.class).annotatedWith(PluginData.class).toInstance(dir);
      }
    };
    Injector injector =
        Guice.createInjector(testModule, FetchAndPushCounter.module());

    pushCounts = getCounter(injector, FetchAndPushCounter.PUSH_COUNTS);
    fetchCounts = getCounter(injector, FetchAndPushCounter.FETCH_COUNTS);
    classUnderTest = injector.getInstance(CounterStore.class);
    classUnderTest.start();
  }

  private Map<NameKey, AtomicLong> getCounter(Injector injector, String cacheName) {
    Key<ConcurrentMap<Project.NameKey, AtomicLong>> key =
        Key.get(
            new TypeLiteral<ConcurrentMap<Project.NameKey, AtomicLong>>() {},
            Names.named(cacheName));
    return injector.getInstance(key);
  }

  @Test
  public void testReadNonExisting() throws Exception {
    assertTrue(fetchCounts.isEmpty());
    assertTrue(pushCounts.isEmpty());
  }

  @Test
  public void testDataIsStoredPersistently() throws Exception {
    fetchCounts.put(new Project.NameKey("p"), new AtomicLong(13l));
    classUnderTest.stop();
    fetchCounts.clear();
    classUnderTest.start();
    assertEquals(1, fetchCounts.size());
    assertEquals(13l, fetchCounts.get(new Project.NameKey("p")).get());
    assertTrue(pushCounts.isEmpty());
  }

  @Test
  public void testOldDataIsOverwritten() throws Exception {
    pushCounts.put(new Project.NameKey("p"), new AtomicLong(13l));
    classUnderTest.stop();
    classUnderTest.start();
    assertEquals(1, pushCounts.size());
    pushCounts.clear();
    classUnderTest.stop();
    assertTrue(fetchCounts.isEmpty());
    assertTrue(pushCounts.isEmpty());
  }

  @Test
  public void testStopClearStart() throws Exception {
    fetchCounts.put(new Project.NameKey("p"), new AtomicLong(13l));
    classUnderTest.stop();
    fetchCounts.clear();
    classUnderTest.start();
    assertEquals(1, fetchCounts.size());
    assertEquals(13l, fetchCounts.get(new Project.NameKey("p")).get());
    assertTrue(pushCounts.isEmpty());
  }

  @After
  public void teardown() {
    for (File file : tmp.listFiles()) {
      file.delete();
    }
    tmp.delete();
  }

}
