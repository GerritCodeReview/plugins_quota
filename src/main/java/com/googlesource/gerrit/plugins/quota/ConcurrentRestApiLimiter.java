// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Singleton
public class ConcurrentRestApiLimiter extends AllRequestFilter {
  private final Pattern servletPath =
      Pattern.compile(
          "^/(?:a/)?" + "(access|accounts|changes|config|groups|plugins|projects|tools)/(.*)$");
  private final Map<String, Integer> connectionsByUser = new ConcurrentHashMap<>();
  private final Integer limit;
  private Provider<CurrentUser> user;

  @Inject
  public ConcurrentRestApiLimiter(ProjectCache projectCache, Provider<CurrentUser> user) {
    limit = 5;
    //        new GlobalQuotaSection(projectCache.getAllProjects().getConfig("quota.config").get())
    //            .getMaxConcurrentRestApiCalls();
    this.user = user;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    AtomicBoolean canPermit = new AtomicBoolean(true);
    String userId = null;

    if (limit != null && isRest(request) && user.get().isIdentifiedUser()) {
      userId = user.get().getAccountId().toString();
      connectionsByUser.compute(
          userId,
          (k, v) -> {
            if (v == null) {
              return 1;
            } else if (v < limit) {
              return v + 1;
            } else {
              canPermit.set(false);
              return v;
            }
          });
    }

    if (!canPermit.getPlain()) {
      ((HttpServletResponse) response)
          .sendError(429, "Too Many Requests, rate limited by maxConcurrentRestApiCallsPerUser");
      return;
    }

    try {
      chain.doFilter(request, response);
    } finally {
      if (userId != null) {
        connectionsByUser.computeIfPresent(
            userId,
            (k, v) -> {
              int updated = v - 1;
              return updated <= 0 ? null : updated;
            });
      }
    }
  }

  boolean isRest(ServletRequest req) {
    return req instanceof HttpServletRequest
        && servletPath.matcher(((HttpServletRequest) req).getServletPath()).matches();
  }
}
