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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.googlesource.gerrit.plugins.quota.usage.UsageDataEventCreator;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class CountModuleTest {

  private File tmp;
  private PersistentCounter pushCounts;
  private PersistentCounter fetchCounts;
  private UsageDataEventCreator pushCreator;
  private UsageDataEventCreator fetchCreator;

  @Before
  public void setup() throws IOException, ConfigInvalidException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();

    final File dir = tmp;

    final ProjectCache projectCache = createNiceMock(ProjectCache.class);
    replay(projectCache);

    final Config config = new BlobBasedConfig(null, new byte[0]);

    Module testModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(config);
        bind(File.class).annotatedWith(SitePath.class).toInstance(dir);
        bind(File.class).annotatedWith(PluginData.class).toInstance(dir);
        bind(ProjectCache.class).toInstance(projectCache);
      }
    };
    Injector injector = Guice.createInjector(testModule, new CountModule());

    pushCounts = getCounter(injector, CountModule.PUSH);
    fetchCounts = getCounter(injector, CountModule.FETCH);

    pushCreator = getCreator(injector, CountModule.PUSH);
    fetchCreator = getCreator(injector, CountModule.FETCH);
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



}
