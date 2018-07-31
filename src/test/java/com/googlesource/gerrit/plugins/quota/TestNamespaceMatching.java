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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

public class TestNamespaceMatching {

  @Test
  public void exactNamespace() {
    String exact = "test/myProject";
    assertTrue(new Namespace(exact).matches(new Project.NameKey("test/myProject")));
    assertFalse(new Namespace(exact).matches(new Project.NameKey("test/myOtherProject")));
  }

  @Test
  public void patternNamespace() {
    String pattern = "test/*";
    assertTrue(new Namespace(pattern).matches(new Project.NameKey("test/myProject")));
    assertFalse(new Namespace(pattern).matches(new Project.NameKey("other/myOtherProject")));
  }

  @Test
  public void regExp() {
    String pattern = "^test/.*/my.*";
    assertTrue(new Namespace(pattern).matches(new Project.NameKey("test/a/myProject")));
    assertTrue(new Namespace(pattern).matches(new Project.NameKey("test/b/myOtherProject")));
    assertFalse(new Namespace(pattern).matches(new Project.NameKey("other/otherProject")));
  }
}
