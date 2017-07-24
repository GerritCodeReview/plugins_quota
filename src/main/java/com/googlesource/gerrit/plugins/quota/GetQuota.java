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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import java.util.concurrent.ExecutionException;

public class GetQuota implements RestReadView<ProjectResource> {

  private final ProjectCache projectCache;
  private final QuotaFinder quotaFinder;
  private final RepoSizeCache repoSizeCache;
  private final boolean enableDiskQuota;
  private static final String UNKNOWN = "UNKNOWN";
  private static final String UNLIMITED = "UNLIMITED";

  @Inject
  public GetQuota(ProjectCache projectCache, QuotaFinder quotaFinder,
      RepoSizeCache repoSizeCache, PluginConfigFactory cfg,
      @PluginName String pluginName) {
    this.projectCache = projectCache;
    this.quotaFinder = quotaFinder;
    this.repoSizeCache = repoSizeCache;
    this.enableDiskQuota =
        cfg.getFromGerritConfig(pluginName).getBoolean("enableDiskQuota", true);
  }

  @Override
  public QuotaInfo apply(ProjectResource rsrc) throws ExecutionException {
    return getInfo(rsrc.getNameKey());
  }

  QuotaInfo getInfo(Project.NameKey n) {
    QuotaInfo qi = new QuotaInfo();
    Long repoSize = repoSizeCache.get(n);
    if (repoSize != -1) {
      qi.repoSize = repoSize.toString();
    } else {
      qi.repoSize = UNKNOWN;
    }

    QuotaSection qs = quotaFinder.firstMatching(n);
    if (qs == null) {
      return qi;
    }

    if (enableDiskQuota) {
      qi.maxRepoSize = qs.getMaxRepoSize().toString();
      qi.namespace = new NamespaceInfo();
      qi.namespace.name = qs.getNamespace();
      long totalSize = 0;
      for (Project.NameKey p : projectCache.all()) {
        if (qs.matches(p)) {
          totalSize += repoSizeCache.get(p);
        }
      }
      qi.namespace.totalSize = Long.toString(totalSize);
      if (qs.getMaxTotalSize() != null) {
        qi.namespace.maxTotalSize = qs.getMaxTotalSize().toString();
      }
    } else {
      qi.maxRepoSize = UNLIMITED;
      qi.namespace = new NamespaceInfo();
      qi.namespace.name = qs.getNamespace();
      qi.namespace.totalSize = UNLIMITED;
      qi.namespace.maxTotalSize = UNLIMITED;
    }
    return qi;
  }

  public static class NamespaceInfo {
    public String name;
    public String totalSize;
    public String maxTotalSize;
  }

  public static class QuotaInfo {
    public String repoSize;
    public String maxRepoSize;
    public NamespaceInfo namespace;
  }
}
