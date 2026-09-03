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
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MaxEndpointConnectionsLimiter extends AllRequestFilter {
  private record EndpointLimit(Pattern pattern, int limit) {}

  private record Acquisition(String scope, EndpointLimit limit) {
    String key() {
      return scope + "::" + limit.pattern().pattern();
    }
  }

  private static final Logger log = LoggerFactory.getLogger(MaxEndpointConnectionsLimiter.class);
  private static final String CONFIG_KEY = "maxConnectionsForEndpoint";
  private static final String GLOBAL_SECTION = "global";
  private static final Pattern CONFIG_VALUE_REGEX = Pattern.compile("(\\d+)\\s+(.+)");

  private final Map<String, Integer> connectionsByAcquisition = new ConcurrentHashMap<>();
  private final Provider<CurrentUser> userProvider;
  private final AccountLimitsFinder accountLimitsFinder;
  private List<EndpointLimit> globalLimits = new ArrayList<>();
  private Map<String, List<EndpointLimit>> limitsByGroup = new LinkedHashMap<>();

  @Inject
  public MaxEndpointConnectionsLimiter(
      Provider<CurrentUser> userProvider, AccountLimitsFinder accountLimitsFinder) {
    this.userProvider = userProvider;
    this.accountLimitsFinder = accountLimitsFinder;
  }

  @Inject
  void init(QuotaConfigFileProvider configPath, ProjectCache projectCache) {
    init(projectCache.getAllProjects().getConfig(configPath.get()).get());
  }

  @VisibleForTesting
  void init(Config cfg) {
    globalLimits.clear();
    limitsByGroup.clear();

    parseLimits(cfg, GLOBAL_SECTION, null, globalLimits);

    for (String group : cfg.getSubsections(AccountLimitsConfig.GROUP_SECTION)) {
      List<EndpointLimit> limits = new ArrayList<>();
      parseLimits(cfg, AccountLimitsConfig.GROUP_SECTION, group, limits);
      limitsByGroup.put(group, limits);
    }
  }

  private void parseLimits(Config cfg, String section, String subsection, List<EndpointLimit> out) {
    for (String value : cfg.getStringList(section, subsection, CONFIG_KEY)) {
      Matcher matcher = CONFIG_VALUE_REGEX.matcher(value.trim());
      if (!matcher.matches()) {
        log.error(
            "Invalid ''{}'' configuration ''{}''; ignoring the configuration entry",
            CONFIG_KEY,
            value);
        continue;
      }
      int limit = Integer.parseInt(matcher.group(1));
      try {
        out.add(new EndpointLimit(Pattern.compile(matcher.group(2)), limit));
      } catch (PatternSyntaxException e) {
        log.error(
            "Invalid ''{}'' pattern ''{}''; ignoring the configuration entry",
            CONFIG_KEY,
            matcher.group(2),
            e);
      }
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if ((globalLimits.isEmpty() && limitsByGroup.isEmpty())
        || !(request instanceof HttpServletRequest)) {
      chain.doFilter(request, response);
      return;
    }

    String path = ((HttpServletRequest) request).getServletPath();
    List<Acquisition> applicable = applicableLimits(path);

    if (applicable.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }

    List<Acquisition> acquired = new ArrayList<>(applicable.size());
    for (Acquisition acquisition : applicable) {
      if (canPermitCall(acquisition)) {
        acquired.add(acquisition);
      } else {
        for (Acquisition toRelease : acquired) {
          markCallComplete(toRelease);
        }
        ((HttpServletResponse) response)
            .sendError(429, "Too Many Requests: rate limited by " + CONFIG_KEY);
        return;
      }
    }

    try {
      chain.doFilter(request, response);
    } finally {
      for (Acquisition acquisition : acquired) {
        markCallComplete(acquisition);
      }
    }
  }

  private List<Acquisition> applicableLimits(String path) {
    List<Acquisition> result = new ArrayList<>();

    for (EndpointLimit limit : globalLimits) {
      if (limit.pattern().matcher(path).matches()) {
        result.add(new Acquisition(GLOBAL_SECTION, limit));
      }
    }

    String matchedGroup = firstMatchingGroup();
    if (matchedGroup != null) {
      for (EndpointLimit limit : limitsByGroup.get(matchedGroup)) {
        if (limit.pattern().matcher(path).matches()) {
          result.add(new Acquisition(matchedGroup, limit));
        }
      }
    }

    return result;
  }

  private String firstMatchingGroup() {
    CurrentUser currentUser = userProvider.get();
    if (!currentUser.isIdentifiedUser()) {
      return null;
    }

    GroupMembership effectiveGroups = currentUser.asIdentifiedUser().getEffectiveGroups();
    for (String group : limitsByGroup.keySet()) {
      if (accountLimitsFinder.isMatching(effectiveGroups, group)) {
        return group;
      }
    }
    return null;
  }

  private boolean canPermitCall(Acquisition acquisition) {
    AtomicBoolean permitted = new AtomicBoolean(false);
    connectionsByAcquisition.compute(
        acquisition.key(),
        (key, count) -> {
          int current = (count == null) ? 0 : count;
          if (current < acquisition.limit().limit()) {
            permitted.setPlain(true);
            return current + 1;
          }
          return current;
        });
    return permitted.getPlain();
  }

  private void markCallComplete(Acquisition acquisition) {
    connectionsByAcquisition.computeIfPresent(
        acquisition.key(),
        (key, count) -> {
          int next = count - 1;
          return next <= 0 ? null : next;
        });
  }
}
