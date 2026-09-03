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

import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
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
  private static final Logger log = LoggerFactory.getLogger(MaxEndpointConnectionsLimiter.class);

  record EndpointLimit(Pattern pattern, int limit) {}

  private static final String CONFIG_KEY = "maxRestApiConnectionsForEndpoint";
  private static final Pattern CONFIG_VALUE_REGEX = Pattern.compile("(\\d+)\\s+(.+)");

  private final Map<String, Integer> connectionsByPattern = new ConcurrentHashMap<>();
  private List<EndpointLimit> endpointLimits = new ArrayList<>();

  @Inject
  void init(ProjectCache projectCache) {
    init(projectCache.getAllProjects().getConfig("quota.config").get());
  }

  void init(Config cfg) {
    List<EndpointLimit> parsed = new ArrayList<>();
    for (String value : cfg.getStringList("global", null, CONFIG_KEY)) {
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
        parsed.add(new EndpointLimit(Pattern.compile(matcher.group(2)), limit));
      } catch (PatternSyntaxException e) {
        log.error(
            "Invalid ''{}'' pattern ''{}''; ignoring the configuration entry",
            CONFIG_KEY,
            matcher.group(2),
            e);
      }
    }
    endpointLimits = parsed;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (endpointLimits.isEmpty() || !(request instanceof HttpServletRequest)) {
      chain.doFilter(request, response);
      return;
    }

    String path = ((HttpServletRequest) request).getServletPath();
    List<EndpointLimit> matches = matchingLimits(path);
    if (matches.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }

    List<EndpointLimit> acquired = new ArrayList<>(matches.size());
    for (EndpointLimit endpointLimit : matches) {
      if (canPermitCall(endpointLimit)) {
        acquired.add(endpointLimit);
      } else {
        for (EndpointLimit toRelease : acquired) {
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
      for (EndpointLimit endpointLimit : acquired) {
        markCallComplete(endpointLimit);
      }
    }
  }

  private List<EndpointLimit> matchingLimits(String path) {
    List<EndpointLimit> matches = new ArrayList<>();
    for (EndpointLimit endpointLimit : endpointLimits) {
      if (endpointLimit.pattern().matcher(path).matches()) {
        matches.add(endpointLimit);
      }
    }
    return matches;
  }

  private boolean canPermitCall(EndpointLimit endpointLimit) {
    AtomicBoolean permitted = new AtomicBoolean(false);
    connectionsByPattern.compute(
        endpointLimit.pattern().pattern(),
        (key, count) -> {
          int current = (count == null) ? 0 : count;
          if (current < endpointLimit.limit()) {
            permitted.setPlain(true);
            return current + 1;
          }
          return current;
        });
    return permitted.getPlain();
  }

  private void markCallComplete(EndpointLimit endpointLimit) {
    connectionsByPattern.computeIfPresent(
        endpointLimit.pattern().pattern(),
        (key, count) -> {
          int next = count - 1;
          return next <= 0 ? null : next;
        });
  }
}
