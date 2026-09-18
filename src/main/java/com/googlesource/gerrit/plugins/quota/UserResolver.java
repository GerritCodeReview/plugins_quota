// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a username from the quota configuration to the username stored on the account.
 *
 * <p>The username a task string carries comes from {@code IdentifiedUser.getUserName()}, which
 * returns the username as stored in the account's external id. A configured username that differs
 * from the stored one, in case or otherwise, therefore matches no task at all. The lookup applies
 * {@code auth.userNameCaseInsensitive}, so which spellings resolve follows the host configuration
 * rather than a rule of the plugin's own.
 */
@Singleton
public class UserResolver {
  private static final Logger log = LoggerFactory.getLogger(UserResolver.class);

  private final AccountCache accountCache;

  @Inject
  UserResolver(AccountCache accountCache) {
    this.accountCache = accountCache;
  }

  /**
   * Returns the username as stored on the account, or empty when no account has the given username.
   *
   * @param user username as spelled in the configuration
   * @param key quota key the username was configured for, for logging
   */
  public Optional<String> storedUsername(String user, String key) {
    Optional<String> stored = accountCache.getByUsername(user).flatMap(AccountState::userName);
    if (stored.isEmpty()) {
      log.warn("No account has the username [{}] configured for {}", user, key);
    }
    return stored;
  }
}
