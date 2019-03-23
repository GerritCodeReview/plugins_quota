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

import com.google.common.cache.LoadingCache;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.quota.Module.Holder;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RestApiRateLimiter extends AllRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RestApiRateLimiter.class);
  private static final int SECONDS_PER_HOUR = 3600;

  static final int SC_TOO_MANY_REQUESTS = 429;

  private final Provider<CurrentUser> user;
  private final LoadingCache<Account.Id, Holder> limitsPerAccount;
  private final LoadingCache<String, Holder> limitsPerRemoteHost;

  private final Pattern servleturi =
      Pattern.compile(
          "^/(?:a/)?"
              + "(access|accounts|changes|config|groups|plugins|projects|Documentation|tools)/(.*)$");

  private final String limitExceededMsg;

  @Inject
  RestApiRateLimiter(
      Provider<CurrentUser> user,
      @Named(HttpModule.CACHE_NAME_RESTAPI_ACCOUNTID)
          LoadingCache<Account.Id, Holder> limitsPerAccount,
      @Named(HttpModule.CACHE_NAME_RESTAPI_REMOTEHOST)
          LoadingCache<String, Holder> limitsPerRemoteHost,
      @Named(RateMsgHelper.RESTAPI_CONFIGURABLE_MSG_ANNOTATION) String limitExceededMsg) {
    this.user = user;
    this.limitsPerAccount = limitsPerAccount;
    this.limitsPerRemoteHost = limitsPerRemoteHost;
    this.limitExceededMsg = limitExceededMsg;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, final FilterChain chain)
      throws IOException, ServletException {
    if (isRest(req)) {
      Holder rateLimiterHolder;
      CurrentUser u = user.get();
      if (u.isIdentifiedUser()) {
        Account.Id accountId = u.asIdentifiedUser().getAccountId();
        try {
          rateLimiterHolder = limitsPerAccount.get(accountId);
        } catch (ExecutionException e) {
          rateLimiterHolder = Holder.EMPTY;
          String msg =
              MessageFormat.format("Cannot get rate limits for account ''{0}''", accountId);
          log.warn(msg, e);
        }
      } else {
        try {
          rateLimiterHolder = limitsPerRemoteHost.get(req.getRemoteHost());
        } catch (ExecutionException e) {
          rateLimiterHolder = Holder.EMPTY;
          String msg =
              MessageFormat.format(
                  "Cannot get rate limits for anonymous access from remote host ''{0}''",
                  req.getRemoteHost());
          log.warn(msg, e);
        }
      }
      if (!rateLimiterHolder.hasGracePermits()
          && rateLimiterHolder.get() != null
          && !rateLimiterHolder.get().tryAcquire()) {
        String msg =
            MessageFormat.format(
                limitExceededMsg,
                rateLimiterHolder.get().getRate() * SECONDS_PER_HOUR,
                rateLimiterHolder.getBurstPermits());
        ((HttpServletResponse) res).sendError(SC_TOO_MANY_REQUESTS, msg);
        return;
      }
    }
    chain.doFilter(req, res);
  }

  boolean isRest(ServletRequest req) {
    return req instanceof HttpServletRequest
        && servlet.matcher(((HttpServletRequest) req).getServletPath()).matches();
  }
}
