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

import static com.googlesource.gerrit.plugins.quota.RestApiRateLimiter.SC_TOO_MANY_REQUESTS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;
import com.googlesource.gerrit.plugins.quota.Module.Holder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RestApiRateLimiterTest {
  private static final String LIMIT_EXCEEDED_MSG =
      "test exceeded message: {0,number,##.##}, {1,number,###}";
  private static final String REMOTE_HOST = "host";
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse res;
  @Mock private FilterChain chain;
  @Mock private Provider<CurrentUser> user;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CurrentUser currentUser;

  @Mock private Account.Id accountId;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Holder holder;

  @Mock private RateLimiter rateLimiter;

  @Mock @GerritServerConfig Config cfg;
  @Mock GenericFactory userFactory;
  @Mock AccountLimitsFinder finder;

  private RestApiRateLimiter restReqFilter;
  private SystemGroupBackend systemGroupBackend;
  private LoadingCache<Account.Id, Holder> limitsPerAccount;
  private LoadingCache<String, Holder> globalLimitsPerAccount;
  private LoadingCache<String, Holder> limitsPerRemoteHost;

  @Before
  public void setUp() throws IOException, ServletException {
    systemGroupBackend = new SystemGroupBackend(cfg);

    limitsPerAccount =
        CacheBuilder.newBuilder()
            .build(new Module.HolderCacheLoaderByAccountId(Type.UPLOADPACK, userFactory, finder));
    limitsPerAccount.put(accountId, holder);

    globalLimitsPerAccount =
        CacheBuilder.newBuilder()
            .build(new Module.HolderCacheLoaderByGlobalAccount(Type.UPLOADPACK, finder));

    limitsPerRemoteHost =
        CacheBuilder.newBuilder()
            .build(
                new Module.HolderCacheLoaderByRemoteHost(
                    Type.UPLOADPACK, systemGroupBackend, finder));
    limitsPerRemoteHost.put(REMOTE_HOST, holder);

    restReqFilter =
        spy(
            new RestApiRateLimiter(
                user,
                limitsPerAccount,
                globalLimitsPerAccount,
                limitsPerRemoteHost,
                LIMIT_EXCEEDED_MSG));
    doReturn(true).when(restReqFilter).isRest(req);
    when(user.get()).thenReturn(currentUser);
    doNothing().when(chain).doFilter(req, res);
  }

  private void setUpRegisteredUser() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(currentUser.asIdentifiedUser().getAccountId()).thenReturn(accountId);
  }

  private void setUpAnonymous() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(false);
    when(req.getRemoteHost()).thenReturn(REMOTE_HOST);
  }

  private void setUpNoQuotaViolation1() {
    when(holder.hasGracePermits()).thenReturn(false);
    when(holder.get()).thenReturn(rateLimiter);
    when(holder.get().tryAcquire()).thenReturn(true);
  }

  private void setUpNoQuotaViolation2() {
    when(holder.hasGracePermits()).thenReturn(true);
  }

  private void setUpQuotaViolation() {
    when(holder.hasGracePermits()).thenReturn(false);
    when(holder.get()).thenReturn(rateLimiter);
    when(holder.get().tryAcquire()).thenReturn(false);
  }

  @Test
  public void testDoFilterQuotaViolation()
      throws IOException, ServletException, ExecutionException {
    setUpRegisteredUser();
    setUpQuotaViolation();
    restReqFilter.doFilter(req, res, chain);
    verify(res).sendError(eq(SC_TOO_MANY_REQUESTS), anyString());
  }

  @Test
  public void testDoFilterNoQuotaViolation()
      throws IOException, ServletException, ExecutionException {
    setUpRegisteredUser();
    setUpNoQuotaViolation1();
    restReqFilter.doFilter(req, res, chain);
    verify(res, times(0)).sendError(eq(SC_TOO_MANY_REQUESTS), anyString());
    setUpNoQuotaViolation2();
    restReqFilter.doFilter(req, res, chain);
    verify(res, times(0)).sendError(eq(SC_TOO_MANY_REQUESTS), anyString());
  }

  @Test
  public void testDoFilterAnonymQuotaViolation()
      throws IOException, ServletException, ExecutionException {
    setUpAnonymous();
    setUpQuotaViolation();
    restReqFilter.doFilter(req, res, chain);
    verify(res).sendError(eq(SC_TOO_MANY_REQUESTS), anyString());
  }

  @Test
  public void testDoFilterAnonymNoQuotaViolation()
      throws IOException, ServletException, ExecutionException {
    setUpAnonymous();
    setUpNoQuotaViolation1();
    restReqFilter.doFilter(req, res, chain);
    verify(res, times(0)).sendError(eq(SC_TOO_MANY_REQUESTS), anyString());
    setUpNoQuotaViolation2();
    restReqFilter.doFilter(req, res, chain);
    verify(res, times(0)).sendError(eq(SC_TOO_MANY_REQUESTS), anyString());
  }
}
