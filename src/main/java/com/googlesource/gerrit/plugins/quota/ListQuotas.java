// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.restapi.project.ListProjects;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.quota.GetQuota.QuotaInfo;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.kohsuke.args4j.Option;

public class ListQuotas implements RestReadView<ConfigResource> {

  private final GetQuota getQuota;
  private final Provider<ListProjects> listProjects;
  private String matchPrefix;

  @Inject
  public ListQuotas(GetQuota getQuota, Provider<ListProjects> listProjects) {
    this.getQuota = getQuota;
    this.listProjects = listProjects;
  }

  @Option(
      name = "--prefix",
      aliases = {"-p"},
      metaVar = "PREFIX",
      usage = "match project prefix")
  public void setMatchPrefix(String matchPrefix) {
    this.matchPrefix = matchPrefix;
  }

  @Override
  public Response apply(ConfigResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Map<String, QuotaInfo> result = Maps.newTreeMap();
    ListProjects lister = listProjects.get();
    lister.setMatchPrefix(matchPrefix);
    for (String projectName : lister.apply().keySet()) {
      Project.NameKey n = Project.nameKey(projectName);
      result.put(projectName, getQuota.getInfo(n));
    }
    return Response.ok(result);
  }
}
