// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProjectResolver {
  private static final Pattern PROJECT_PATTERN = Pattern.compile("\\s+/?(.*)\\s+(\\(\\S+\\))$");

  private final ProjectCache projectCache;

  @Inject
  ProjectResolver(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public Optional<Project.NameKey> estimateProject(WorkQueue.Task<?> task) {
    String taskStr = task.toString();
    if (!isGitCommand(task)) {
      return Optional.empty();
    }

    Matcher matcher = PROJECT_PATTERN.matcher(taskStr);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String name = matcher.group(1);
    if (name.startsWith("a/")
        && projectCache != null
        && projectCache.get(Project.NameKey.parse(name)).isEmpty()) {
      name = name.substring(2);
    }
    return Optional.of(Project.NameKey.parse(name));
  }

  static boolean isGitCommand(WorkQueue.Task<?> task) {
    String taskStr = task.toString();
    return taskStr.startsWith("git-upload-pack") || taskStr.startsWith("git-receive-pack");
  }
}
