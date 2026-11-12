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

import static com.google.common.truth.Truth.assertWithMessage;

import java.util.regex.Pattern;
import org.junit.Test;

public class RestApiPathsTest {
  // The prefixes RestApiRateLimiter uses, including the "tools" servlet path that is also a
  // common project namespace.
  private static final Pattern REST_PREFIXES =
      Pattern.compile(
          "^/(?:a/)?(access|accounts|changes|config|groups|plugins|projects|Documentation|tools)/(.*)$");

  @Test
  public void restCallsAndToolDownloadsAreRestApi() {
    for (String path :
        new String[] {
          "/changes/",
          "/a/changes/123/revisions/current/mergeable",
          "/tools/hooks/commit-msg",
          "/a/projects/tools%2Fexample/branches",
        }) {
      assertWithMessage(path).that(RestApiPaths.isRestApi(REST_PREFIXES, path)).isTrue();
    }
  }

  @Test
  public void gitOverHttpForProjectsUnderRestPrefixesIsNotRestApi() {
    for (String path :
        new String[] {
          "/tools/example/info/refs",
          "/a/tools/example/info/refs",
          "/a/tools/example/git-upload-pack",
          "/tools/example.git/git-upload-pack",
          "/a/tools/group/example/git-receive-pack",
          "/plugins/example/git-upload-pack",
        }) {
      assertWithMessage(path).that(RestApiPaths.isRestApi(REST_PREFIXES, path)).isFalse();
    }
  }

  @Test
  public void pathsOutsideRestPrefixesAreNotRestApi() {
    for (String path : new String[] {"/infra/example/info/refs", "/c/infra/example/+/1"}) {
      assertWithMessage(path).that(RestApiPaths.isRestApi(REST_PREFIXES, path)).isFalse();
    }
  }
}
