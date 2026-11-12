// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.CoreDownloadSchemes;
import com.google.gerrit.httpd.GitOverHttpServlet;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.regex.Pattern;

/** Decides which request paths the REST API limits count. */
@Singleton
class RestApiPaths {
  /** Paths counted by the restapi rate limit. */
  @VisibleForTesting
  static final Pattern RATE_LIMITED =
      Pattern.compile(
          "^/(?:a/)?"
              + "(access|accounts|changes|config|groups|plugins|projects|Documentation|tools)/(.*)$");

  /** Paths counted by maxConnectionsPerUserForTask. */
  @VisibleForTesting
  static final Pattern CONNECTION_LIMITED =
      Pattern.compile(
          "^/(?:a/)?(access|accounts|changes|config|groups|plugins|projects|tools)/.*$");

  /**
   * Git over HTTP requests, matched with the regex Gerrit core uses to route requests to its Git
   * servlet. A project whose name starts with one of the prefixes above, such as {@code
   * tools/example}, is cloned through paths like {@code /a/tools/example/info/refs} that would
   * otherwise count as REST calls. The regex is a public constant of Gerrit core rather than plugin
   * API; RestApiPathsTest pins the paths it must keep matching.
   */
  private static final Pattern GIT_OVER_HTTP = Pattern.compile(GitOverHttpServlet.URL_REGEX);

  /**
   * Gerrit only installs its Git servlet when an HTTP download scheme is enabled. Otherwise the
   * same paths reach REST or plugin views and must stay counted.
   */
  private final boolean gitOverHttpEnabled;

  @Inject
  RestApiPaths(DownloadConfig downloadConfig) {
    this(isGitOverHttpEnabled(downloadConfig.getDownloadSchemes()));
  }

  @VisibleForTesting
  RestApiPaths(boolean gitOverHttpEnabled) {
    this.gitOverHttpEnabled = gitOverHttpEnabled;
  }

  boolean isRateLimited(String path) {
    return isRestApi(RATE_LIMITED, path);
  }

  boolean isConnectionLimited(String path) {
    return isRestApi(CONNECTION_LIMITED, path);
  }

  private boolean isRestApi(Pattern prefixes, String path) {
    boolean servedByGitServlet = gitOverHttpEnabled && GIT_OVER_HTTP.matcher(path).matches();
    return prefixes.matcher(path).matches() && !servedByGitServlet;
  }

  /** Same condition GitOverHttpModule uses to decide whether to serve Git over HTTP. */
  private static boolean isGitOverHttpEnabled(Set<String> downloadSchemes) {
    return downloadSchemes.contains(CoreDownloadSchemes.ANON_HTTP)
        || downloadSchemes.contains(CoreDownloadSchemes.HTTP);
  }
}
