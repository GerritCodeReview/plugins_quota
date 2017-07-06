// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class ConfigProvider implements Provider<Config> {
  private static final String QUOTA_CONFIG_FILE = "quota.config";

  private final ProjectCache projectCache;
  private final PluginConfigFactory pluginConfigFactory;
  private final String pluginName;
  private final boolean configInReviewSite;

  @Inject
  public ConfigProvider(ProjectCache projectCache,
      PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    this.projectCache = projectCache;
    this.pluginConfigFactory = pluginConfigFactory;
    this.pluginName = pluginName;
    configInReviewSite = !getFromReviewSite().getSections().isEmpty();
    if (configInReviewSite && !getFromAllProjects().getSections().isEmpty()) {
      throw new ProvisionException(QUOTA_CONFIG_FILE
          + " must be configure either in All-projects or in review_site/etc, no both");
    }
  }

  private Config getFromAllProjects() {
    return projectCache.getAllProjects().getConfig(QUOTA_CONFIG_FILE).get();
  }

  private Config getFromReviewSite() {
    return pluginConfigFactory.getGlobalPluginConfig(pluginName);
  }

  @Override
  public Config get() {
    if (configInReviewSite) {
      return getFromReviewSite();
    }
    return getFromAllProjects();
  }
}
