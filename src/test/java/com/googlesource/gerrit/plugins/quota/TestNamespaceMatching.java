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

import com.google.gerrit.entities.Project;
import org.junit.Test;

public class TestNamespaceMatching {

  @Test
  public void exactNamespace() {
    String exact = "test/myProject";
    assertTrue(new Namespace(exact).matches(Project.nameKey("test/myProject")));
    assertFalse(new Namespace(exact).matches(Project.nameKey("test/myOtherProject")));
  }

  @Test
  public void patternNamespace() {
    String pattern = "test/*";
    assertTrue(new Namespace(pattern).matches(Project.nameKey("test/myProject")));
    assertFalse(new Namespace(pattern).matches(Project.nameKey("other/myOtherProject")));
  }

  @Test
  public void regExp() {
    String pattern = "^test/.*/my.*";
    assertTrue(new Namespace(pattern).matches(Project.nameKey("test/a/myProject")));
    assertTrue(new Namespace(pattern).matches(Project.nameKey("test/b/myOtherProject")));
    assertFalse(new Namespace(pattern).matches(Project.nameKey("other/otherProject")));
  }

  @Test
  public void globalNamespace() {
    String global = "^.*";
    assertTrue(new Namespace(global).matches(Project.nameKey("any/project")));
    assertTrue(new Namespace(global).matches(Project.nameKey("completely/different/repo")));
  }

@Test
  public void combinationOverlap() {
    String projectName = "test/team-alpha/web-app";
    Project.NameKey project = Project.nameKey(projectName);
    assertTrue(new Namespace("test/team-alpha/web-app").matches(project));
    assertTrue(new Namespace("test/team-alpha/*").matches(project));
    assertTrue(new Namespace("^test/.*/web-.*").matches(project));
    assertTrue(new Namespace("^.*").matches(project));
  }

  @Test
  public void fallbackAndNegativeMatching() {
    Namespace fallback = new Namespace("internal/*");
    assertTrue(fallback.matches(Project.nameKey("internal/tool-a")));
    assertFalse(fallback.matches(Project.nameKey("public/common-library")));
    assertFalse(fallback.matches(Project.nameKey("external/client-repo")));
  }

  @Test
  public void edgeCaseMatching() {
    String global = "^.*";
    assertTrue(new Namespace(global).matches(Project.nameKey("root-project")));
    assertTrue(new Namespace("a/b/*").matches(Project.nameKey("a/b/c/d/e")));
  }
}