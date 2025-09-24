package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class MaxConnectionsLimiter extends AllRequestFilter {
  record Limit(String group, Integer restApiLimit) {}

  private static final String GLOBAL_KEY = "GLOBAL";
  private static final String CONFIG_KEY = "maxConnectionsPerUserForTask";
  private static final String CONFIG_REST_API = "rest-api";
  private static final Pattern REST_API_REGEX = Pattern.compile("(\\d+)\\s+" + CONFIG_REST_API);

  private static final Pattern REST_API_PATH_PATTERN =
      Pattern.compile(
          "^/(?:a/)?(access|accounts|changes|config|groups|plugins|projects|tools)/.*$");

  private final Map<String, Integer> connectionsByUser = new ConcurrentHashMap<>();
  private final Provider<CurrentUser> userProvider;
  private final AccountLimitsFinder accountLimitsFinder;
  private final List<Limit> limits = new ArrayList<>();
  private Limit globalLimit;

  @Inject
  public MaxConnectionsLimiter(
      AccountLimitsFinder accountLimitsFinder, Provider<CurrentUser> userProvider) {
    this.accountLimitsFinder = accountLimitsFinder;
    this.userProvider = userProvider;
  }

  @Inject
  void init(ProjectCache projectCache) {
    Config cfg = projectCache.getAllProjects().getConfig("quota.config").get();
    for (String group : cfg.getSubsections(AccountLimitsConfig.GROUP_SECTION)) {
      String val = cfg.getString(AccountLimitsConfig.GROUP_SECTION, group, CONFIG_KEY);
      Matcher matcher = REST_API_REGEX.matcher(val);
      if (matcher.find()) {
        limits.add(new Limit(group, Integer.parseInt(matcher.group())));
      }
    }

    String globalConfig = cfg.getString(AccountLimitsConfig.GROUP_SECTION, GLOBAL_KEY, CONFIG_KEY);
    Matcher matcher = REST_API_REGEX.matcher(globalConfig);
    if (matcher.find()) {
      globalLimit = new Limit(GLOBAL_KEY, Integer.parseInt(matcher.group()));
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (isRestApiRequest(request)) {
      CurrentUser currentUser = userProvider.get();

      if (currentUser.isIdentifiedUser()) {
        String userId = currentUser.getAccountId().toString();
        Optional<Integer> limit = getEffectiveRestApiLimit(currentUser.asIdentifiedUser());

        if (limit.isPresent()) {
          try {
            if (!canPermitCall(userId, limit.get())) {
              ((HttpServletResponse) response)
                  .sendError(429, "Too Many Requests: rate limited by " + CONFIG_KEY);
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

  private Optional<Integer> getEffectiveRestApiLimit(IdentifiedUser user) {
    List<Integer> result = new ArrayList<>();

    for (Limit limit : limits) {
      if (accountLimitsFinder.isMatching(user.getEffectiveGroups(), limit.group())) {
        result.add(limit.restApiLimit());
        break; // only consider the first matching group
      }
    }

    if (globalLimit != null) {
      result.add(globalLimit.restApiLimit());
    }

    return result.stream().filter(l -> l > 0).min(Integer::compareTo);
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
