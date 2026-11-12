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

import com.google.gerrit.httpd.GitOverHttpServlet;
import java.util.regex.Pattern;

/** Decides which request paths the REST API limits count. */
final class RestApiPaths {
  /**
   * Git over HTTP requests, matched exactly as Gerrit routes them to its Git servlet. A project
   * whose name starts with one of the REST prefixes, such as {@code tools/example}, is cloned
   * through paths like {@code /a/tools/example/info/refs} that the prefix patterns would otherwise
   * count as REST calls.
   */
  private static final Pattern GIT_OVER_HTTP = Pattern.compile(GitOverHttpServlet.URL_REGEX);

  private RestApiPaths() {}

  /** Returns whether {@code path} matches {@code restPrefixes} and is not Git over HTTP. */
  static boolean isRestApi(Pattern restPrefixes, String path) {
    return restPrefixes.matcher(path).matches() && !GIT_OVER_HTTP.matcher(path).matches();
  }
}
