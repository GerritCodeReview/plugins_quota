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
import org.junit.Test;

public class QuotaMessageInterpolatorTest {

  private static final Project.NameKey PROJECT = Project.nameKey("foo/bar");

  @Test
  public void interpolateReplacesAllVariables() {
    String template =
        "Project ${project} exceeds quota: maximum=${maximum}, available=${available}.";

    String result = QuotaMessageInterpolator.interpolate(template, PROJECT, 100L, 200L);

    assertThat(result).isEqualTo("Project foo/bar exceeds quota: maximum=200, available=100.");
  }

  @Test
  public void interpolateHandlesRepeatedVariables() {
    String template =
        "Project ${project}, project=${project}, available=${available}, maximum=${maximum}.";

    String result = QuotaMessageInterpolator.interpolate(template, PROJECT, 42L, 1337L);

    assertThat(result).isEqualTo("Project foo/bar, project=foo/bar, available=42, maximum=1337.");
  }

  @Test
  public void interpolateLeavesUnknownVariablesUntouched() {
    String template = "Project ${project} exceeded quota ${unknown} with available=${available}.";

    String result = QuotaMessageInterpolator.interpolate(template, PROJECT, 10L, 20L);

    assertThat(result).isEqualTo("Project foo/bar exceeded quota ${unknown} with available=10.");
  }

  @Test
  public void interpolateWorksWithEmptyTemplate() {
    String template = "";

    String result = QuotaMessageInterpolator.interpolate(template, PROJECT, 10L, 20L);

    assertThat(result).isEmpty();
  }

  @Test
  public void interpolateWorksWhenNumbersAreZero() {
    String template = "Project ${project} has maximum=${maximum} and available=${available}.";

    String result = QuotaMessageInterpolator.interpolate(template, PROJECT, 0L, 0L);

    assertThat(result).isEqualTo("Project foo/bar has maximum=0 and available=0.");
  }
}
