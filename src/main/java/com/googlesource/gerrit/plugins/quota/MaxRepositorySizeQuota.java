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
import com.google.gerrit.server.config.ReceivePackInitializer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      File gitDir = git.getDirectory();
      return getDiskUsage(gitDir);
    } finally {
      git.close();
    }
  }

  private long getDiskUsage(File file) {
    if (file.isFile()) {
      return file.length();
    }

    long size = 0;
    for (File f : file.listFiles()) {
      size += getDiskUsage(f);
    }
    return size;
  }
}
