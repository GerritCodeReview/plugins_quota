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

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.quota.Module.Holder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse res;
  @Mock private FilterChain chain;
  @Mock private Provider<CurrentUser> user;
  @Mock private LoadingCache<Account.Id, Holder> limitsPerAccount;
  @Mock private LoadingCache<String, Holder> limitsPerRemoteHost;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CurrentUser currentUser;

  @Mock private Account.Id accountId;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Holder holder;

  @Mock private RateLimiter rateLimiter;
  private RestApiRateLimiter restReqFilter;

  @Before
  public void setUp() throws IOException, ServletException {
    restReqFilter =
        spy(
            new RestApiRateLimiter(
                user, limitsPerAccount, limitsPerRemoteHost, LIMIT_EXCEEDED_MSG));
    doReturn(true).when(restReqFilter).isRest(req);
    when(user.get()).thenReturn(currentUser);
    doNothing().when(chain).doFilter(req, res);
  }

  private void setUpRegisteredUser() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(currentUser.asIdentifiedUser().getAccountId()).thenReturn(accountId);
    when(limitsPerAccount.get(accountId)).thenReturn(holder);
  }

  private void setUpRegisteredUserExecutionException() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(currentUser.asIdentifiedUser().getAccountId()).thenReturn(accountId);
    when(limitsPerAccount.get(accountId)).thenThrow(new ExecutionException(null));
  }

  private void setUpAnonymous() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(false);
    when(req.getRemoteHost()).thenReturn("host");
    when(limitsPerRemoteHost.get("host")).thenReturn(holder);
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
  public void testDoFilterCacheMiss() throws IOException, ServletException, ExecutionException {
    setUpRegisteredUserExecutionException();
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
