package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ConcurrentRestApiLimiter extends AllRequestFilter {
  private static final Pattern REST_API_PATH_PATTERN =
      Pattern.compile(
          "^/(?:a/)?(access|accounts|changes|config|groups|plugins|projects|tools)/.*$");

  private final Map<String, Integer> connectionsByUser = new ConcurrentHashMap<>();
  private final Provider<CurrentUser> userProvider;
  private final AccountLimitsFinder accountLimitsFinder;

  @Inject
  public ConcurrentRestApiLimiter(
      AccountLimitsFinder accountLimitsFinder, Provider<CurrentUser> userProvider) {
    this.accountLimitsFinder = accountLimitsFinder;
    this.userProvider = userProvider;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (isRestApiRequest(request)) {
      CurrentUser currentUser = userProvider.get();

      if (currentUser.isIdentifiedUser()) {
        String userId = currentUser.getAccountId().toString();
        Optional<Integer> limit = getEffectiveLimit(currentUser);

        if (limit.isPresent()) {
          try {
            if (!canPermitCall(userId, limit.get())) {
              ((HttpServletResponse) response)
                  .sendError(
                      429, "Too Many Requests: rate limited by maxConcurrentRestApiCallsPerUser");
              return;
            }
            chain.doFilter(request, response);
          } finally {
            markCallComplete(userId);
          }
          return;
        }
      }
    }

    chain.doFilter(request, response);
  }

  private boolean isRestApiRequest(ServletRequest req) {
    return req instanceof HttpServletRequest
        && REST_API_PATH_PATTERN.matcher(((HttpServletRequest) req).getServletPath()).matches();
  }

  private Optional<Integer> getEffectiveLimit(CurrentUser user) {
    List<Integer> limits = new ArrayList<>();

    accountLimitsFinder
        .firstMatching(AccountLimitsConfig.Type.CONCURRENT_RESTAPI, user.asIdentifiedUser())
        .ifPresent(rl -> limits.add(rl.getConcurrentRestApiRequests()));

    accountLimitsFinder
        .getGlobalRateLimit(AccountLimitsConfig.Type.CONCURRENT_RESTAPI)
        .ifPresent(rl -> limits.add(rl.getConcurrentRestApiRequests()));

    return limits.stream().filter(l -> l > 0).min(Integer::compareTo);
  }

  private boolean canPermitCall(String userId, int limit) {
    return connectionsByUser.compute(
            userId,
            (u, c) -> {
              c = c == null ? 0 : c;
              return c < limit ? c + 1 : c;
            })
        <= limit;
  }

  private void markCallComplete(String userId) {
    connectionsByUser.computeIfPresent(
        userId,
        (u, c) -> {
          c--;
          return c <= 0 ? null : c;
        });
  }
}
