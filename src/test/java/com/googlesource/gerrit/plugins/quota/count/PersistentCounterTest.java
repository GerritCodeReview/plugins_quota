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

import static org.junit.Assert.assertEquals;

import com.google.gerrit.reviewdb.client.Project;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class PersistentCounterTest {

  private File tmp;
  private PersistentCounter counter;

  @Before
  public void setup() throws IOException {
    tmp = File.createTempFile("quota-test", "dir");
    tmp.delete();
    tmp.mkdir();

    counter = new PersistentCounter(tmp, "test");
  }

  @Test
  public void testEmptyCounter() {
    checkCounting(counter, 0l);
  }

  @Test
  public void testFirstValue() {
    checkCounting(counter, 1l);
  }

  @Test
  public void testNextValue() {
    checkCounting(counter, 2l);
  }

  private void checkCounting(PersistentCounter counter, long count) {
    Project.NameKey p = new Project.NameKey("p");
    assertEquals(0l, counter.getAndReset(p));
    for (long i = 0l; i < count; i++) {
      counter.increment(p);
    }
    assertEquals(count, counter.getAndReset(p));
  }

  @After
  public void teardown() {
    counter.close();
    for (File file : tmp.listFiles()) {
      file.delete();
    }
    tmp.delete();
  }

}
