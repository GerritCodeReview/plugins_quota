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

import com.google.gerrit.server.git.WorkQueue;
import java.util.regex.Pattern;

public abstract class TaskQuotaForTask extends TaskQuotaWithPermits {
  public final String taskGroup;
  protected final Pattern taskRegex;

  public TaskQuotaForTask(String taskGroup, int permits) {
    super(permits);
    this.taskGroup = taskGroup;

    if (taskGroup.startsWith("^") && taskGroup.endsWith("$")) {
      this.taskRegex = Pattern.compile(taskGroup);
    } else {
      this.taskRegex = null;
    }
  }

  @Override
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
