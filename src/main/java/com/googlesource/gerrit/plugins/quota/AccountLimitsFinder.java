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

import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.restapi.group.GroupsCollection;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.RateLimit;
import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;

import java.util.*;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountLimitsFinder {
  private static final Logger log = LoggerFactory.getLogger(AccountLimitsFinder.class);

  private final ProjectCache projectCache;
  private final GroupsCollection groupsCollection;

  @Inject
  AccountLimitsFinder(ProjectCache projectCache, GroupsCollection groupsCollection) {
    this.projectCache = projectCache;
    this.groupsCollection = groupsCollection;
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

  public Optional<String> firstMatching(Collection<String> groups, IdentifiedUser user) {
    GroupMembership memberShip = user.getEffectiveGroups();
    for (String group : groups) {
      if (isMatching(memberShip, group)) {
        return Optional.of(group);
      }
    }
    return Optional.empty();
  }

  public boolean isMatching(GroupMembership membership, String groupName) {
    try {
      Optional<GroupDescription.Internal> maybeInternalGroup =
          groupsCollection
              .parse(TopLevelResource.INSTANCE, IdString.fromDecoded(groupName))
              .asInternalGroup();
      if (!maybeInternalGroup.isPresent()) {
        log.debug("Ignoring limits for non-internal group ''{}'' in quota.config", groupName);
      } else if (membership.contains(maybeInternalGroup.get().getGroupUUID())) {
        return true;
      }
    } catch (ResourceNotFoundException e) {
      log.debug("Ignoring limits for unknown group ''{}'' in quota.config", groupName);
    } catch (AuthException e) {
      log.debug("Ignoring limits for non-visible group ''{}'' in quota.config", groupName);
    }

    return false;
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
    Config cfg = projectCache.getAllProjects().getConfig("quota.config").get();
    AccountLimitsConfig limitsCfg = cfg.get(KEY);
    return limitsCfg.getRatelimits(type);
  }
}
