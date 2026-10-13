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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.project.ProjectCache;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;

public class TestNamespaceMatching {

  private static final Set<String> UPLOAD_PACK_ALIASES =
      TaskParser.SUPPORTED_TASKS_BY_GROUP.get("uploadpack");

  private static final Set<String> RECEIVE_PACK_ALIASES =
      TaskParser.SUPPORTED_TASKS_BY_GROUP.get("receivepack");

  private static final Set<String> INDEX_TASK_PREFIXES =
      TaskParser.SUPPORTED_TASKS_BY_GROUP.get("indexing");

  private static final ImmutableSet<String> GIT_ALIASES =
      ImmutableSet.<String>builder()
          .addAll(UPLOAD_PACK_ALIASES)
          .addAll(RECEIVE_PACK_ALIASES)
          .build();

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
    assertTrue(pattern.matcher("indexing").matches());

    assertTrue(pattern.matcher("^gerrit.query.*$").matches());
    assertTrue(pattern.matcher("^gerrit[ ]+stream-events.*$").matches());
    assertTrue(pattern.matcher("^.*$").matches());

    assertFalse(pattern.matcher("invalidpack").matches());
    assertFalse(pattern.matcher("uploadpack-extended").matches());
  }

  @Test
  public void configPatterns() {
    Pattern queuePattern = TaskQuotaForTaskForQueue.CONFIG_PATTERN;
    assertTrue(queuePattern.matcher("10 uploadpack queue-name").matches());
    assertTrue(queuePattern.matcher("10 indexing Index-Batch").matches());
    assertTrue(queuePattern.matcher("5 ^gerrit.query.status:open.*$ operational-queue").matches());
    assertFalse(queuePattern.matcher("10 unknownpack queue-name").matches());

    Pattern userPattern = TaskForUserForQueueConfig.CONFIG_PATTERN;
    assertTrue(userPattern.matcher("10 uploadpack some-user queue-name").matches());
    assertTrue(userPattern.matcher("5 ^gerrit.ls-members.*$ user_123 batch-queue").matches());
    assertFalse(userPattern.matcher("10 unknownpack some-user queue-name").matches());
  }

  @Test
  public void taskGroupMatchesEveryCommandAlias() {
    for (String alias : UPLOAD_PACK_ALIASES) {
      Task<?> task = task(alias + " /example.git (admin)");
      assertTrue(alias, new TaskGroup("uploadpack").isApplicable(task));
      assertFalse(alias, new TaskGroup("receivepack").isApplicable(task));
    }

    for (String alias : RECEIVE_PACK_ALIASES) {
      Task<?> task = task(alias + " /example.git (admin)");
      assertTrue(alias, new TaskGroup("receivepack").isApplicable(task));
      assertFalse(alias, new TaskGroup("uploadpack").isApplicable(task));
    }

    for (String prefix : INDEX_TASK_PREFIXES) {
      Task<?> task = task(prefix + "1234 example");
      assertTrue(prefix, new TaskGroup("indexing").isApplicable(task));
      assertFalse(prefix, new TaskGroup("uploadpack").isApplicable(task));
      assertFalse(prefix, new TaskGroup("receivepack").isApplicable(task));
    }
  }

  @Test
  public void projectEstimatedFromEveryCommandAlias() {
    ProjectResolver resolver = new ProjectResolver(mock(ProjectCache.class));
    Optional<Project.NameKey> expected = Optional.of(Project.nameKey("example"));

    for (String alias : GIT_ALIASES) {
      assertEquals(alias, expected, resolver.estimateProject(task(alias + " example.git (admin)")));
      assertEquals(
          alias, expected, resolver.estimateProject(task(alias + " /example.git (admin)")));
      assertEquals(
          alias, expected, resolver.estimateProject(task(alias + " /./example.git (admin)")));
    }
  }

  @Test
  public void projectEstimatedFromEveryIndexTask() {
    ProjectResolver resolver = new ProjectResolver(mock(ProjectCache.class));

    for (String prefix : INDEX_TASK_PREFIXES) {
      assertEquals(
          prefix,
          Optional.of(Project.nameKey("example")),
          resolver.estimateProject(task(prefix + "1234 example")));
      assertEquals(
          prefix,
          Optional.of(Project.nameKey("test/example")),
          resolver.estimateProject(task(prefix + "1234 test/example")));
    }
  }

  /** Index tasks name the project as {@code Project.NameKey.toString()} writes it. */
  @Test
  public void encodedProjectNameDecodedFromIndexTask() {
    ProjectResolver resolver = new ProjectResolver(mock(ProjectCache.class));
    Project.NameKey project = Project.nameKey("test/an example");

    assertEquals(
        Optional.of(project), resolver.estimateProject(task("index-change-1234 " + project)));
  }

  /**
   * Indexing never runs over HTTP, so a leading "a/" in an index task is the start of a project's
   * name rather than the URL prefix it is for a fetch or push, and must not be stripped.
   */
  @Test
  public void projectNameStartingWithHttpPrefixKeptFromIndexTask() {
    ProjectResolver resolver = new ProjectResolver(mock(ProjectCache.class));

    assertEquals(
        Optional.of(Project.nameKey("a/example")),
        resolver.estimateProject(task("index-change-1234 a/example")));
  }

  /** Gerrit releases before I3004b8c9 named no project, and the change number is not one. */
  @Test
  public void indexTaskWithoutProjectHasNoEstimatedProject() {
    ProjectResolver resolver = new ProjectResolver(mock(ProjectCache.class));
    assertEquals(Optional.empty(), resolver.estimateProject(task("index-change-1234")));
  }

  @Test
  public void unsupportedTaskHasNoEstimatedProject() {
    ProjectResolver resolver = new ProjectResolver(mock(ProjectCache.class));
    assertEquals(Optional.empty(), resolver.estimateProject(task("gerrit stream-events (admin)")));
  }

  private static Task<?> task(String taskString) {
    Task<?> task = mock(Task.class);
    when(task.toString()).thenReturn(taskString);
    return task;
  }
}
