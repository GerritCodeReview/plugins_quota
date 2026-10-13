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
import com.googlesource.gerrit.plugins.quota.TaskParser.ProjectNameForm;
import java.nio.file.Paths;
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
   * Matched against the task string with the command name already removed, for example:
   *
   * <p>" example.git (admin)"
   *
   * <p>" /example.git (admin)"
   *
   * <p>" /./example.git (admin)"
   *
   * <p>The command name is removed first so that the pattern cannot match inside it. The pattern is
   * not anchored at its start and the earliest match wins, so given a full task string whose
   * command name contains a space, such as "git upload-pack", the match starts at that space and
   * captures "upload-pack /example.git" as the project.
   */
  private static final Pattern REPOSITORY_THEN_USER_PATTERN =
      Pattern.compile("\\s+/?(.*)\\s+(\\(\\S+\\))$");

  /**
   * Matched against the task string with the task name already removed, for example:
   *
   * <p>"1234 example"
   *
   * <p>Index tasks end with {@code Project.NameKey.toString()}, which percent encodes everything
   * outside a small safe set and writes a space as "+", so the project is the last whitespace
   * separated run and cannot itself contain whitespace. A task string without one is from a Gerrit
   * too old to name the project, and resolves to no project rather than to the change number.
   */
  private static final Pattern TRAILING_PROJECT_PATTERN = Pattern.compile("\\s+(\\S+)$");

  private static final String HTTP_PREFIX = "a/";

  private final ProjectCache projectCache;

  @Inject
  ProjectResolver(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public Optional<Project.NameKey> estimateProject(WorkQueue.Task<?> task) {
    String taskStr = task.toString();
    Optional<String> matchedTask = TaskParser.matchedTask(taskStr);
    if (matchedTask.isEmpty()) {
      return Optional.empty();
    }
    ProjectNameForm form = TaskParser.PROJECT_NAME_FORM_BY_TASK.get(matchedTask.get());
    if (form == null) {
      return Optional.empty();
    }

    String arguments = taskStr.substring(matchedTask.get().length());
    return switch (form) {
      case REPOSITORY_THEN_USER -> repositoryThenUser(arguments);
      case TRAILING_PROJECT -> trailingProject(arguments);
    };
  }

  /**
   * Resolves the repository the client asked for to a project. The name arrives as a URL path, so
   * it can be slash prefixed and can carry the prefix Gerrit's authenticated HTTP endpoints use.
   */
  private Optional<Project.NameKey> repositoryThenUser(String arguments) {
    Matcher matcher = REPOSITORY_THEN_USER_PATTERN.matcher(arguments);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String name = normalizeRepoName(matcher.group(1));

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
   * Reads the project Gerrit itself wrote into the task string. It already names a project, so none
   * of the guessing the repository form needs applies, only the decoding that undoes {@code
   * Project.NameKey.toString()}.
   */
  private static Optional<Project.NameKey> trailingProject(String arguments) {
    Matcher matcher = TRAILING_PROJECT_PATTERN.matcher(arguments);
    return matcher.find() ? Optional.of(Project.NameKey.parse(matcher.group(1))) : Optional.empty();
  }

  /**
   * Drops leading slashes, duplicate slashes, and "." segments. For example: "/./foo//bar/"
   * normalizes to "foo/bar".
   */
  private static String normalizeRepoName(String name) {
    String normalized = Paths.get(name).normalize().toString();
    if (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }
}
