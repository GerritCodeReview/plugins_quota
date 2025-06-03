package com.googlesource.gerrit.plugins.quota;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.AbstractModule;
import org.eclipse.jgit.lib.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Semaphore;

public class TaskParkerModule extends AbstractModule {
  @Inject TaskParkerGenerator generator;

  @Override
  protected void configure() {
    generator.generate().forEach((name, taskParker) ->
        bind(WorkQueue.TaskListener.class)
            .annotatedWith(Exports.named(name))
            .toInstance(taskParker)
    );
  }
}
