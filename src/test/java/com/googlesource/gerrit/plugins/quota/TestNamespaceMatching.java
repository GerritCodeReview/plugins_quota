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
import java.util.regex.Pattern;
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
  public void taskGroupPattern() {
    Pattern pattern = Pattern.compile(TaskParser.TASK_GROUP_PATTERN);
    assertTrue(pattern.matcher("uploadpack").matches());
    assertTrue(pattern.matcher("receivepack").matches());
    assertTrue(pattern.matcher("^git-upload-.*$").matches());
    assertTrue(pattern.matcher("^.*$").matches());
    assertFalse(pattern.matcher("invalidpack").matches());
    assertFalse(pattern.matcher("uploadpack-extended").matches());
  }

  @Test
  public void configPatterns() {
    Pattern queuePattern = TaskQuotaForTaskForQueue.CONFIG_PATTERN;
    assertTrue(queuePattern.matcher("10 uploadpack queue-name").matches());
    assertTrue(queuePattern.matcher("5 ^git-upload-pack$ operational-queue").matches());
    assertFalse(queuePattern.matcher("10 unknownpack queue-name").matches());

    Pattern userPattern = TaskQuotaForTaskForQueueForUser.CONFIG_PATTERN;
    assertTrue(userPattern.matcher("10 uploadpack some-user queue-name").matches());
    assertTrue(userPattern.matcher("5 ^.*$ user_123 operational-queue").matches());
    assertFalse(userPattern.matcher("10 unknownpack some-user queue-name").matches());
  }
}
