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

import com.google.gerrit.entities.Project;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class responsible for expanding variables in size-quota-exceeded message templates.
 *
 * <p>This interpolator replaces simple {@code ${var}} placeholders inside a message template with
 * runtime values derived from size quota evaluation. It currently supports exactly three variables:
 *
 * <ul>
 *   <li>{@code ${project}} — expanded to the project name (e.g. {@code "foo/bar"}).
 *   <li>{@code ${available}} — the number of remaining bytes before quota is exceeded.
 *   <li>{@code ${maximum}} — the configured maximum allowed value for the applicable quota.
 * </ul>
 *
 * <p>Unknown variables are left untouched, allowing safe forward-compatibility and making it easier
 * to spot configuration mistakes.
 */
class QuotaSizeMessageInterpolator {
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  /**
   * Expands variables inside the quota-exceeded message template.
   *
   * <p>Supported variables:
   *
   * <ul>
   *   <li>{@code ${project}} — project name
   *   <li>{@code ${available}} — remaining quota in bytes/tokens
   *   <li>{@code ${maximum}} — configured upper limit for the quota
   * </ul>
   *
   * <p>Unknown placeholders are preserved unchanged, e.g. {@code ${foo}} remains {@code ${foo}}.
   *
   * @param template raw template read from configuration
   * @param project the affected project
   * @param availableSize remaining size/tokens before exceeding quota
   * @param maximumSize configured maximum for the quota
   * @return the template with variables replaced
   */
  static String interpolate(
      String template, Project.NameKey project, long availableSize, long maximumSize) {

    Map<String, String> vars =
        Map.of(
            "project", project.get(),
            "available", String.valueOf(availableSize),
            "maximum", String.valueOf(maximumSize));

    Matcher m = VAR_PATTERN.matcher(template);
    StringBuilder sb = new StringBuilder();

    while (m.find()) {
      String var = m.group(1);
      String replacement = vars.getOrDefault(var, m.group(0));
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }

    m.appendTail(sb);
    return sb.toString();
  }
}
