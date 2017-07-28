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

import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;

import java.util.concurrent.ExecutionException;

public class GetQuota implements RestReadView<ProjectResource> {

  private final ProjectCache projectCache;
  private final QuotaFinder quotaFinder;
  private final RepoSizeCache repoSizeCache;

  @Inject
  public GetQuota(ProjectCache projectCache, QuotaFinder quotaFinder,
      RepoSizeCache repoSizeCache) {
    this.projectCache = projectCache;
    this.quotaFinder = quotaFinder;
    this.repoSizeCache = repoSizeCache;
  }

  @Override
  public QuotaInfo apply(ProjectResource rsrc) throws ExecutionException {
    return getInfo(rsrc.getNameKey());
  }

  QuotaInfo getInfo(Project.NameKey n) throws ExecutionException {
    QuotaInfo qi = new QuotaInfo();
    qi.repoSize = repoSizeCache.get(n);

    QuotaSection qs = quotaFinder.firstMatching(n);
    if (qs == null) {
      return qi;
    }

    qi.maxRepoSize = qs.getMaxRepoSize();
    qi.namespace = new NamespaceInfo();
    qi.namespace.name = qs.getNamespace();
    long totalSize = 0;
    for (Project.NameKey p : projectCache.all()) {
      if (qs.matches(p)) {
        totalSize += repoSizeCache.get(p);
      }
    }
    qi.namespace.totalSize = totalSize;
    qi.namespace.maxTotalSize = qs.getMaxTotalSize();
    return qi;
  }

  public static class NamespaceInfo {
    public String name;
    public long totalSize;
    public Long maxTotalSize;
  }

  public static class QuotaInfo {
    public long repoSize;
    public Long maxRepoSize;
    public NamespaceInfo namespace;
  }
}
