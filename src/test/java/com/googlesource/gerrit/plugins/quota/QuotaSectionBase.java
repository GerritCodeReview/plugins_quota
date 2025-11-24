// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class QuotaSectionBase {

  protected static final Project.NameKey PROJECT = Project.nameKey("foo/bar");

  protected abstract QuotaSection createQuotaSection(Config cfg);

  @Test
  public void fallsBackToDefaultExceededMessageUnknownRequestedWhenNotConfigured() {
    Config cfg = new Config();
    QuotaSection section = createQuotaSection(cfg);

    String message = section.quotaSizeExceededMessage(PROJECT, 100L, 200L, Optional.empty());

    assertThat(message)
        .isEqualTo("Project foo/bar exceeds quota: max=200 bytes, available=100 bytes.");
  }

  @Test
  public void usesSizeExceededMessageUnknownRequestedWhenConfigured() {
    Config cfg = new Config();
    cfg.setString(
        createQuotaSection(cfg).section(),
        createQuotaSection(cfg).subSection(),
        QuotaSection.KEY_SIZE_EXCEEDED_UNKNOWN_REQUESTED_MSG,
        "Custom for ${project}: max=${maximum}, avail=${available}");
    QuotaSection section = createQuotaSection(cfg);

    String message = section.quotaSizeExceededMessage(PROJECT, 10L, 20L, Optional.empty());

    assertThat(message).isEqualTo("Custom for foo/bar: max=20, avail=10");
  }

  @Test
  public void fallsBackToDefaultExceededMessageKnownRequestedWhenNotConfigured() {
    Config cfg = new Config();
    QuotaSection section = createQuotaSection(cfg);

    String message = section.quotaSizeExceededMessage(PROJECT, 100L, 200L, Optional.of(300L));

    assertThat(message)
        .isEqualTo("Requested space [300] is bigger then available [100] for repository foo/bar");
  }

  @Test
  public void usesSizeExceededMessageKnownRequestedWhenConfigured() {
    Config cfg = new Config();
    cfg.setString(
        createQuotaSection(cfg).section(),
        createQuotaSection(cfg).subSection(),
        QuotaSection.KEY_SIZE_EXCEEDED_KNOWN_REQUESTED_MSG,
        "Custom for ${project}: max=${maximum}, avail=${available}, requested=${requested}");
    QuotaSection section = createQuotaSection(cfg);

    String message = section.quotaSizeExceededMessage(PROJECT, 10L, 20L, Optional.of(30L));

    assertThat(message).isEqualTo("Custom for foo/bar: max=20, avail=10, requested=30");
  }
}
