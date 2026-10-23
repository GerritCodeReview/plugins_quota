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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinStartForUserForQueueQuota extends MinStartQuota {
  private static final Logger log = LoggerFactory.getLogger(MinStartForUserForQueueQuota.class);
  public static final String KEY = "minStartForUserForQueue";
  public static final Pattern CONFIG_PATTERN =
      Pattern.compile("(\\d+)\\s+" + TaskParser.USER_PATTERN + "\\s+(.+)");

  private final UserResolver userResolver;

  @Inject
  public MinStartForUserForQueueQuota(ProjectResolver projectResolver, UserResolver userResolver) {
    super(projectResolver);
    this.userResolver = userResolver;
  }

  @Override
  public Optional<TaskQuota> build(QuotaSection qs, String cfg) {
    if (qs.isFallbackQuota()) {
      log.warn("{} is not applicable in fallback quota section", KEY);
      return Optional.empty();
    }

    Matcher matcher = CONFIG_PATTERN.matcher(cfg);
    if (!matcher.matches()) {
      log.error("Invalid configuration entry for {} [{}]", KEY, cfg);
      return Optional.empty();
    }

    String queue = matcher.group(3);
    int capacity = Integer.parseInt(matcher.group(1));
    userResolver
        .storedUsername(matcher.group(2), KEY)
        .ifPresent(
            user ->
                reservation(qs)
                    .capacity(capacity)
                    .onQueue(queue)
                    .matching(task -> TaskParser.isUser(task, user))
                    .register());

    return Optional.empty();
  }
}
