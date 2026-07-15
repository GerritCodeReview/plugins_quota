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
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProjectResolver {
  public static final Logger log = LoggerFactory.getLogger(ProjectResolver.class);

  /**
   * Example task.toString():
   *
   * <p>git-upload-pack example.git (admin)
   *
   * <p>git-receive-pack /example.git (admin)
   *
   * <p>git-upload-pack /./example.git (admin)
   */
  private static final Pattern PROJECT_PATTERN = Pattern.compile("\\s+/?(.*)\\s+(\\(\\S+\\))$");

  private static final String HTTP_PREFIX = "a/";

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
    String name = normalizePath(matcher.group(1));

    Project.NameKey candidate = Project.NameKey.parse(name);
    if (!name.startsWith(HTTP_PREFIX)) {
      return Optional.of(candidate);
    }

    try {
      if (projectCache.get(candidate).isEmpty()) {
        return Optional.of(Project.NameKey.parse(name.substring(HTTP_PREFIX.length())));
      }
    } catch (StorageException e) {
      log.warn("Exception while looking up project cache", e);
    }

    return Optional.of(candidate);
  }

  /**
   * Drops leading slashes, duplicate slashes, and "." segments. For example: "/./foo//bar/"
   * normalizes to "foo/bar".
   */
  private static String normalizePath(String name) {
    String normalized = Paths.get(name).normalize().toString();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }

  static boolean isGitCommand(String taskStr) {
    return taskStr.startsWith("git-upload-pack") || taskStr.startsWith("git-receive-pack");
  }
}
