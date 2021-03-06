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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.quota.Module.Holder;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimitUploadListener implements UploadValidationListener {
  private static final int SECONDS_PER_HOUR = 3600;
  private static final Logger log = LoggerFactory.getLogger(RateLimitUploadListener.class);
  private static final Method createStopwatchMethod;
  private static final Constructor<?> constructor;

  static {
    try {
      Class<?> sleepingStopwatchClass =
          Class.forName("com.google.common.util.concurrent.RateLimiter$SleepingStopwatch");
      createStopwatchMethod = sleepingStopwatchClass.getDeclaredMethod("createFromSystemTimer");
      createStopwatchMethod.setAccessible(true);
      Class<?> burstyRateLimiterClass =
          Class.forName("com.google.common.util.concurrent.SmoothRateLimiter$SmoothBursty");
      constructor = burstyRateLimiterClass.getDeclaredConstructors()[0];
      constructor.setAccessible(true);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // shouldn't happen
      throw new RuntimeException("Failed to prepare loading RateLimiter via reflection", e);
    }
  }

  /**
   * Create a custom instance of RateLimiter by accessing the non-public constructor of the
   * implementation class SmoothRateLimiter.SmoothBursty through reflection.
   *
   * <p>RateLimiter's implementation class SmoothRateLimiter.SmoothBursty allows to collect permits
   * during idle times which can be used to send bursts of requests exceeding the average rate until
   * the stored permits are consumed. If the rate per second is 0.2 and you wait 20 seconds you can
   * acquire 4 permits which in average matches the configured rate limit of 0.2 requests/second. If
   * the permitted rate is smaller than 1 per second the standard implementation doesn't allow any
   * bursts since it hard-codes the maximum time which can be used to collect stored permits to 1
   * second.
   *
   * <p>Build jobs fetching updates from Gerrit are typically triggered by events which can arrive
   * in bursts. Hence the standard RateLimiter seems not to be the right choice at least for fetch
   * requests where we probably want to limit the rate to less than 1 request per second per user.
   *
   * <p>The used constructor can't be accessed through a public method yet hence use reflection to
   * instantiate it.
   *
   * @see "https://github.com/google/guava/issues/1974"
   * @param permitsPerSecond the new stable rate of this {@code RateLimiter}
   * @param maxBurstSeconds The maximum number of permits that can be saved.
   * @return a new RateLimiter
   */
  @VisibleForTesting
  static RateLimiter createSmoothBurstyRateLimiter(
      double permitsPerSecond, double maxBurstSeconds) {
    RateLimiter rl;
    try {
      Object stopwatch = createStopwatchMethod.invoke(null);
      rl = (RateLimiter) constructor.newInstance(stopwatch, maxBurstSeconds);
      rl.setRate(permitsPerSecond);
    } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
      // shouldn't happen
      throw new RuntimeException(e);
    }
    return rl;
  }

  private final Provider<CurrentUser> user;
  private final LoadingCache<Account.Id, Holder> limitsPerAccount;
  private final LoadingCache<String, Holder> limitsPerRemoteHost;
  private final String limitExceededMsg;

  @Inject
  RateLimitUploadListener(
      Provider<CurrentUser> user,
      @Named(Module.CACHE_NAME_ACCOUNTID) LoadingCache<Account.Id, Holder> limitsPerAccount,
      @Named(Module.CACHE_NAME_REMOTEHOST) LoadingCache<String, Holder> limitsPerRemoteHost,
      @Named(RateMsgHelper.UPLOADPACK_CONFIGURABLE_MSG_ANNOTATION) String limitExceededMsg) {
    this.user = user;
    this.limitsPerAccount = limitsPerAccount;
    this.limitsPerRemoteHost = limitsPerRemoteHost;
    this.limitExceededMsg = limitExceededMsg;
  }

  @Override
  public void onBeginNegotiate(
      Repository repository,
      Project project,
      String remoteHost,
      UploadPack up,
      Collection<? extends ObjectId> wants,
      int cntOffered)
      throws ValidationException {
    RateLimiter limiter = null;
    CurrentUser u = user.get();
    if (u.isIdentifiedUser()) {
      Account.Id accountId = u.asIdentifiedUser().getAccountId();
      try {
        limiter = limitsPerAccount.get(accountId).get();
      } catch (ExecutionException e) {
        log.warn("Cannot get rate limits for account ''{}''", accountId, e);
      }
    } else {
      try {
        limiter = limitsPerRemoteHost.get(remoteHost).get();
      } catch (ExecutionException e) {
        log.warn(
            "Cannot get rate limits for anonymous access from remote host ''{}''", remoteHost, e);
      }
    }
    if (limiter != null && !limiter.tryAcquire()) {
      throw new RateLimitException(
          MessageFormat.format(limitExceededMsg, limiter.getRate() * SECONDS_PER_HOUR));
    }
  }

  @Override
  public void onPreUpload(
      Repository repository,
      Project project,
      String remoteHost,
      UploadPack up,
      Collection<? extends ObjectId> wants,
      Collection<? extends ObjectId> haves)
      throws ValidationException {}
}
