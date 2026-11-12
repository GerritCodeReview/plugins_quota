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

import static com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.GLOBAL_SECTION;
import static com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.KEY;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.RateLimit;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountLimitsFinder {
  private static final Logger log = LoggerFactory.getLogger(AccountLimitsFinder.class);

  private final ProjectCache projectCache;
  private final SystemGroupBackend systemGroupBackend;
  private final GroupCache groupCache;
  private final GroupResolver groupResolver;
  private final QuotaConfigFileProvider configPath;

  @Inject
  AccountLimitsFinder(
      ProjectCache projectCache,
      SystemGroupBackend systemGroupBackend,
      GroupCache groupCache,
      GroupResolver groupResolver,
      QuotaConfigFileProvider configPath) {
    this.projectCache = projectCache;
    this.systemGroupBackend = systemGroupBackend;
    this.groupCache = groupCache;
    this.groupResolver = groupResolver;
    this.configPath = configPath;
  }

  /**
   * @param type type of rate limit
   * @param user identified user
   * @return the rate limit matching the first configured group limit the given user is a member of
   */
  public Optional<RateLimit> firstMatching(AccountLimitsConfig.Type type, IdentifiedUser user) {
    Optional<Map<String, AccountLimitsConfig.RateLimit>> limits = getRatelimits(type);
    if (limits.isPresent()) {
      GroupMembership memberShip = user.getEffectiveGroups();
      for (String groupName : limits.get().keySet()) {
        RateLimit groupLimit = limits.get().get(groupName);
        if (groupLimit != null && isMatching(memberShip, groupName)) {
          return Optional.of(groupLimit);
        }
      }
    }
    return Optional.empty();
  }

  public boolean isMatching(GroupMembership membership, String groupName) {
    Optional<AccountGroup.UUID> uuid = resolveGroup(groupName);
    if (uuid.isEmpty()) {
      log.debug("Ignoring limits for unknown group ''{}'' in quota.config", groupName);
      return false;
    }
    return membership.contains(uuid.get());
  }

  /**
   * Resolves a group named in quota.config without checking whether the current user can see it.
   * Visibility does not matter for a membership test, and the result must not depend on which
   * request happens to trigger the lookup, since limits are cached per account.
   *
   * <p>System groups such as "Registered Users" and internal groups are looked up by name. Other
   * values, such as group UUIDs, fall back to GroupResolver.
   */
  private Optional<AccountGroup.UUID> resolveGroup(String groupName) {
    GroupReference systemGroup = GroupBackends.findExactSuggestion(systemGroupBackend, groupName);
    if (systemGroup != null) {
      return Optional.of(systemGroup.getUUID());
    }

    Optional<AccountGroup.UUID> internalGroup =
        groupCache.get(AccountGroup.nameKey(groupName)).map(g -> g.getGroupUUID());
    if (internalGroup.isPresent()) {
      return internalGroup;
    }

    return Optional.ofNullable(groupResolver.parseId(groupName))
        .map(GroupDescription.Basic::getGroupUUID);
  }

  /**
   * @param type type of rate limit
   * @return global rate limit
   */
  public Optional<RateLimit> getGlobalRateLimit(Type type) {
    return getRatelimits(type).map(map -> map.get(GLOBAL_SECTION));
  }

  /**
   * @param type type of rate limit
   * @param groupName name of group to lookup up rate limit for
   * @return rate limit
   */
  public Optional<RateLimit> getRateLimit(Type type, String groupName) {
    if (getRatelimits(type).isPresent()) {
      return Optional.ofNullable(getRatelimits(type).get().get(groupName));
    }
    return Optional.empty();
  }

  /**
   * @param type type of rate limit
   * @return map of rate limits per group name
   */
  private Optional<Map<String, RateLimit>> getRatelimits(Type type) {
    Config cfg = projectCache.getAllProjects().getConfig(configPath.get()).get();
    AccountLimitsConfig limitsCfg = cfg.get(KEY);
    return limitsCfg.getRatelimits(type);
  }
}
