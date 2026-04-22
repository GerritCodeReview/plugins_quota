package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.server.git.WorkQueue;

import java.util.Set;
import java.util.regex.Pattern;

public class TaskGroup {
  public final String taskGroup;
  protected final Pattern taskRegex;

  public TaskGroup(String taskGroup) {
    this.taskGroup = taskGroup;

    if (taskGroup.startsWith("^") && taskGroup.endsWith("$")) {
      this.taskRegex = Pattern.compile(taskGroup);
    } else {
      this.taskRegex = null;
    }
  }

  public boolean isApplicable(WorkQueue.Task<?> task) {
    if (taskRegex != null) {
      return taskRegex.matcher(task.toString()).find();
    }

    Set<String> supported = TaskParser.SUPPORTED_TASKS_BY_GROUP.get(taskGroup);
    if (supported != null) {
      return supported.stream().anyMatch(t -> task.toString().startsWith(t));
    }
    return false;
  }
}
