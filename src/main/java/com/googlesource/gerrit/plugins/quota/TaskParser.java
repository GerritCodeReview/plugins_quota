package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskParser {
  public static final Pattern USER_EXTRACT_PATTERN = Pattern.compile("\\(([a-z0-9]+)\\)$");

  public static Optional<String> user(WorkQueue.Task<?> task) {
    Matcher matcher = USER_EXTRACT_PATTERN.matcher(task.toString());
    return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
  }
}
