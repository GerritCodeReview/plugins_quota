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

import org.junit.Test;

public class RestApiPathsTest {
  private final RestApiPaths gitOverHttpEnabled = new RestApiPaths(true);
  private final RestApiPaths gitOverHttpDisabled = new RestApiPaths(false);

  @Test
  public void restCallsAndToolDownloadsAreCounted() {
    for (String path :
        new String[] {
          "/a/plugins/",
          "/plugins/gitiles/",
          "/tools/hooks/commit-msg",
          "/a/changes/123/revisions/current/mergeable",
          "/a/projects/plugins%2Freplication/branches",
          // A trailing slash is not a Git URL.
          "/a/tools/example/info/refs/",
        }) {
      assertCounted(gitOverHttpEnabled, path, true);
    }
  }

  @Test
  public void gitOverHttpUnderRestPrefixesIsNotCounted() {
    for (String path :
        new String[] {
          "/tools/example/info/refs",
          "/a/tools/example/info/refs",
          "/a/tools/example/git-upload-pack",
          "/a/tools/example/git-receive-pack",
          "/tools/example.git/git-upload-pack",
          "/a/tools/group/example/git-upload-pack",
          "/a/plugins/replication/info/refs",
          "/a/plugins/replication/git-upload-pack",
          // Gerrit's Git servlet takes these too, so they are Git, not REST.
          "/a/projects/tools%2Fexample/info/refs",
          "/a/changes/1/info/refs",
        }) {
      assertCounted(gitOverHttpEnabled, path, false);
    }
  }

  @Test
  public void gitPathsAreCountedWhenGitOverHttpIsDisabled() {
    // Without an HTTP download scheme Gerrit has no Git servlet, so these paths reach REST or
    // plugin views and must stay limited.
    for (String path :
        new String[] {"/a/tools/example/info/refs", "/a/plugins/replication/git-upload-pack"}) {
      assertCounted(gitOverHttpDisabled, path, true);
    }
  }

  @Test
  public void pathsOutsideRestPrefixesAreNotCounted() {
    for (String path :
        new String[] {
          "/a/infra/example/info/refs",
          "/p/tools/example/info/refs",
          "/c/infra/example/+/1",
        }) {
      assertCounted(gitOverHttpEnabled, path, false);
    }
  }

  @Test
  public void documentationIsRateLimitedButNotConnectionLimited() {
    assertWithMessage("rate limited")
        .that(gitOverHttpEnabled.isRateLimited("/Documentation/index.html"))
        .isTrue();
    assertWithMessage("connection limited")
        .that(gitOverHttpEnabled.isConnectionLimited("/Documentation/index.html"))
        .isFalse();
  }

  private static void assertCounted(RestApiPaths paths, String path, boolean expected) {
    assertWithMessage("rate limited: " + path).that(paths.isRateLimited(path)).isEqualTo(expected);
    assertWithMessage("connection limited: " + path)
        .that(paths.isConnectionLimited(path))
        .isEqualTo(expected);
  }
}
