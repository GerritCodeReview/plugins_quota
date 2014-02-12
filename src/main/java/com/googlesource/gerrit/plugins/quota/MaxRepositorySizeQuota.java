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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.lang.mutable.MutableLong;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class MaxRepositorySizeQuota implements ReceivePackInitializer {
  private static final Logger log = LoggerFactory
      .getLogger(MaxRepositorySizeQuota.class);

  private final QuotaFinder quotaFinder;
  private final GitRepositoryManager gitManager;

  @Inject
  MaxRepositorySizeQuota(QuotaFinder quotaFinder, GitRepositoryManager gitManager) {
    this.quotaFinder = quotaFinder;
    this.gitManager = gitManager;
  }

  @Override
  public void init(Project.NameKey project, ReceivePack rp) {
    QuotaSection quotaSection = quotaFinder.firstMatching(project);
    if (quotaSection == null) {
      return;
    }

    Long maxRepoSize = quotaSection.getMaxRepoSize();
    if (maxRepoSize == null) {
      return;
    }

    try {
      long maxPackSize = Math.max(0, maxRepoSize - getDiskUsage(project));
      rp.setMaxPackSizeLimit(maxPackSize);
    } catch (IOException e) {
      log.warn("Couldn't setMaxPackSizeLimit on receive-pack for "
          + project.get(), e);
    }
  }

  private long getDiskUsage(Project.NameKey project) throws IOException {
    Repository git = gitManager.openRepository(project);
    try {
      return getDiskUsage(git.getDirectory());
    } finally {
      git.close();
    }
  }

  private static long getDiskUsage(File dir) throws IOException {
    final MutableLong size = new MutableLong();
    Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
          throws IOException {
        if (attrs.isRegularFile()) {
          size.add(attrs.size());
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return size.longValue();
  }
}
