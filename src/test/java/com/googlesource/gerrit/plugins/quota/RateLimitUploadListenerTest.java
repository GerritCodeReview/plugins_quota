// Copyright (C) 2014 The Android Open Source Project
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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.quota.Module.Holder;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RateLimitUploadListenerTest {
  private RateLimitUploadListener uploadHook;
  @Mock private Provider<CurrentUser> user;
  @Mock private LoadingCache<Account.Id, Holder> limitsPerAccount;
  @Mock private LoadingCache<String, Holder> limitsPerRemoteHost;
  private static final String limitExceededMsg = "test exceeded message: {0,number,##.##}";
  private static final String remoteHost = "host";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CurrentUser currentUser;

  @Mock private Account.Id accountId;
  @Mock private Holder holder;
  @Mock private RateLimiter limiter;

  @Before
  public void setUp() {
    uploadHook =
        spy(
            new RateLimitUploadListener(
                user, limitsPerAccount, limitsPerRemoteHost, limitExceededMsg));
    when(user.get()).thenReturn(currentUser);
  }

  private void setUpRegisteredUser() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(currentUser.asIdentifiedUser().getAccountId()).thenReturn(accountId);
    when(limitsPerAccount.get(accountId)).thenReturn(holder);
    when(holder.get()).thenReturn(limiter);
  }

  private void setUpRegisteredUserExecutionException() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(currentUser.asIdentifiedUser().getAccountId()).thenReturn(accountId);
    when(limitsPerAccount.get(accountId)).thenThrow(new ExecutionException(null));
    when(accountId.toString()).thenReturn("123");
  }

  private void setUpAnonymous() throws ExecutionException {
    when(currentUser.isIdentifiedUser()).thenReturn(false);
    when(limitsPerRemoteHost.get(remoteHost)).thenReturn(holder);
    when(holder.get()).thenReturn(limiter);
  }

  private void setUpNoQuotaViolation() {
    when(limiter.tryAcquire()).thenReturn(true);
  }

  private void setUpQuotaViolation() {
    when(limiter.tryAcquire()).thenReturn(false);
  }

  @Test(expected = RateLimitException.class)
  public void testNegotiationQuotaViolation() throws ExecutionException, ValidationException {
    setUpRegisteredUser();
    setUpQuotaViolation();
    uploadHook.onBeginNegotiate(null, null, remoteHost, null, null, 0);
  }

  @Test
  public void testNegotiationNoQuotaViolation() throws ExecutionException, ValidationException {
    setUpRegisteredUser();
    setUpNoQuotaViolation();
    uploadHook.onBeginNegotiate(null, null, remoteHost, null, null, 0);
    verify(limiter, times(0)).getRate();
  }

  @Test
  public void testNegotiationCacheMiss() throws ExecutionException, ValidationException {
    setUpRegisteredUserExecutionException();
    uploadHook.onBeginNegotiate(null, null, remoteHost, null, null, 0);
    verify(limiter, times(0)).getRate();
  }

  @Test(expected = RateLimitException.class)
  public void testNegotiationAnonymQuotaViolation() throws ExecutionException, ValidationException {
    setUpAnonymous();
    setUpQuotaViolation();
    uploadHook.onBeginNegotiate(null, null, remoteHost, null, null, 0);
  }

  @Test
  public void testNegotiationAnonymNoQuotaViolation()
      throws ExecutionException, ValidationException {
    setUpAnonymous();
    setUpNoQuotaViolation();
    uploadHook.onBeginNegotiate(null, null, remoteHost, null, null, 0);
    verify(limiter, times(0)).getRate();
  }
}
