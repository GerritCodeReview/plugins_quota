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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Project;

import org.junit.Test;

public class TestNamespaceMatching {

  @Test
  public void exactNamespace() {
    String exact = "test/myProject";
    Project.NameKey p = new Project.NameKey("test/myProject");
    Project.NameKey other = new Project.NameKey("test/myOtherProject");
    Namespace n = new Namespace(exact, p);
    assertTrue(n.applies());
    assertTrue(n.matches(p));
    assertFalse(n.matches(other));

    assertFalse(new Namespace(exact, other).applies());
  }

  @Test
  public void patternNamespace() {
    String pattern = "test/*";
    Project.NameKey p = new Project.NameKey("test/myProject");
    Project.NameKey other = new Project.NameKey("other/myOtherProject");
    Namespace n = new Namespace(pattern, p);
    assertTrue(n.applies());
    assertTrue(n.matches(p));
    assertFalse(n.matches(other));

    assertFalse(new Namespace(pattern, other).applies());
  }

  @Test
  public void forEachFolderNamespace() {
    String forEach = "?/*";
    Project.NameKey p = new Project.NameKey("test/myProject");
    Project.NameKey p2 = new Project.NameKey("test/myProject2");
    Project.NameKey other = new Project.NameKey("other/myOtherProject");
    Namespace n = new Namespace(forEach, p);
    assertTrue(n.applies());
    assertTrue(n.matches(p));
    assertTrue(n.matches(p2));
    assertFalse(n.matches(other));
    assertEquals("test/*", n.get());

    assertTrue(new Namespace(forEach, other).applies());
  }

  @Test
  public void forEachSubfolderNamespace() {
    String forEach = "test/?/*";
    Project.NameKey p = new Project.NameKey("test/a/myProject");
    Project.NameKey p2 = new Project.NameKey("test/a/myProject2");
    Project.NameKey p3 = new Project.NameKey("test/b/myProject3");
    Project.NameKey other = new Project.NameKey("other/a/myOtherProject");
    Namespace n = new Namespace(forEach, p);
    assertTrue(n.applies());
    assertTrue(n.matches(p));
    assertTrue(n.matches(p2));
    assertFalse(n.matches(p3));
    assertFalse(n.matches(other));
    assertEquals("test/a/*", n.get());

    assertTrue(new Namespace(forEach, p2).applies());
    assertTrue(new Namespace(forEach, p3).applies());
    assertFalse(new Namespace(forEach, other).applies());
  }
}
