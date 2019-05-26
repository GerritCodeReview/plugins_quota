// Copyright (C) 2019 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.quota.Module.Holder;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Blocks extends AllRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(Blocks.class);

  private final Provider<CurrentUser> user;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountLimitsFinder finder;

  @Inject
  Blocks(
      Provider<CurrentUser> user,
      IdentifiedUser.GenericFactory userFactory,
      AccountLimitsFinder finder) {
    this.user = user;
    this.userFactory = userFactory;
    this.finder = finder;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (getBlockValue()
        && request instanceof HttpServletRequest
        && response instanceof HttpServletResponse
        && shouldBlock((HttpServletRequest) request)) {
      ((HttpServletResponse) response).sendError(SC_SERVICE_UNAVAILABLE, "You have been blocked!");
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean shouldBlock(HttpServletRequest request) {
    String method = request.getMethod();
    return !"GET".equals(method);
  }

  private boolean getBlockValue() {
    CurrentUser u = user.get();
    if (u.isIdentifiedUser()) {
      Account.Id accountId = u.asIdentifiedUser().getAccountId();
      Optional<AccountLimitsConfig.Block> blocks = finder.firstMatchingBlock(AccountLimitsConfig.Type.BLOCK, userFactory.create(accountId));
      if (blocks.isPresent()) {
        return blocks.get().getBlockValue();
      }
    }
    return false;
  }
}
