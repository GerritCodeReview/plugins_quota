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
  /**
   * Example task.toString():
   *
   * <p>git-upload-pack example.git (admin)
   *
   * <p>git-receive-pack /example.git (admin)
   */
  private static final Pattern PROJECT_PATTERN = Pattern.compile("\\s+/?(.*)\\s+(\\(\\S+\\))$");

  private final ProjectCache projectCache;

  @Inject
  ProjectResolver(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public Optional<Project.NameKey> estimateProject(WorkQueue.Task<?> task) {
    String taskStr = task.toString();
    if (!isGitCommand(taskStr)) {
      return Optional.empty();
    }

    Matcher matcher = PROJECT_PATTERN.matcher(taskStr);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String name = matcher.group(1);
    Project.NameKey candidate = Project.NameKey.parse(name);
    if (!name.startsWith("/a") || projectCache == null) {
      return Optional.of(candidate);
    }

    if (projectCache.get(candidate).isEmpty()) {
      return Optional.of(Project.NameKey.parse(name.substring(2)));
    }

    return Optional.of(candidate);
  }

  static boolean isGitCommand(String taskStr) {
    return taskStr.startsWith("git-upload-pack") || taskStr.startsWith("git-receive-pack");
  }
}
