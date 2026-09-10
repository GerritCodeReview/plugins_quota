// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.server.git.WorkQueue;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TaskParser {
  /**
   * Task names a group applies to. Gerrit registers each Git command under several names (see
   * {@code DefaultCommandModule}): the hyphenated top level aliases, and the space separated forms
   * produced by {@code DispatchCommand} for nested commands. All of them reach the work queue and
   * must be counted against the same quota.
   */
  public static final Map<String, Set<String>> SUPPORTED_TASKS_BY_GROUP =
      Map.of(
          "uploadpack",
          Set.of("git-upload-pack", "git upload-pack"),
          "receivepack",
          Set.of(
              "git-receive-pack",
              "git receive-pack",
              "gerrit-receive-pack",
              "gerrit receive-pack"));

  /**
   * Groups whose tasks are Git commands, that is, tasks whose string representation carries the
   * repository name. Groups added to {@link #SUPPORTED_TASKS_BY_GROUP} in future are not Git
   * commands unless listed here, and no project can be derived from them.
   */
  public static final Set<String> GIT_COMMAND_GROUPS = Set.of("uploadpack", "receivepack");

  public static final Set<String> GIT_COMMANDS =
      GIT_COMMAND_GROUPS.stream()
          .map(SUPPORTED_TASKS_BY_GROUP::get)
          .flatMap(Set::stream)
          .collect(Collectors.toUnmodifiableSet());

  public static final String TASK_GROUP_PATTERN =
      "(\\^[^$]*\\$|" + String.join("|", SUPPORTED_TASKS_BY_GROUP.keySet()) + ")";
  public static final String USER_PATTERN = "([\\-_A-Za-z0-9]+)";
  public static final Pattern USER_EXTRACT_PATTERN_FROM_TASK_STRING =
      Pattern.compile("\\(" + USER_PATTERN + "\\)$");

  public static Optional<String> user(WorkQueue.Task<?> task) {
    Matcher matcher = USER_EXTRACT_PATTERN_FROM_TASK_STRING.matcher(task.toString());
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  public static boolean isUser(WorkQueue.Task<?> task, String user) {
    return user(task).map(user::equals).orElse(false);
  }

  /**
   * Returns the supported task name that {@code taskStr} starts with. The longest one wins, so the
   * result stays correct if a task name is ever a prefix of another.
   */
  public static Optional<String> matchedTask(String taskStr) {
    return SUPPORTED_TASKS_BY_GROUP.values().stream()
        .flatMap(Set::stream)
        .filter(taskStr::startsWith)
        .max(Comparator.comparingInt(String::length));
  }

  public static boolean matchesGroup(String taskStr, String taskGroup) {
    Set<String> supported = SUPPORTED_TASKS_BY_GROUP.get(taskGroup);
    return supported != null && supported.stream().anyMatch(taskStr::startsWith);
  }
}
