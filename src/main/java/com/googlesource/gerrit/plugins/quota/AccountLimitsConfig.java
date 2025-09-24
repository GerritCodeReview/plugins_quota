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

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.ConfigEnum;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type.*;

public class AccountLimitsConfig {
  private static final Pattern PATTERN =
      Pattern.compile("^\\s*(\\d+)\\s*/\\s*(.*)\\s*burst\\s*(\\d+)$");
  private static final Logger log = LoggerFactory.getLogger(AccountLimitsConfig.class);
  static final String GROUP_SECTION = "group";
  static final String GLOBAL_SECTION = "global";
  static final SectionParser<AccountLimitsConfig> KEY =
      new SectionParser<AccountLimitsConfig>() {
        @Override
        public AccountLimitsConfig parse(final Config cfg) {
          return new AccountLimitsConfig(cfg);
        }
      };

  public static class RateLimit {
    public Type getType() {
      return type;
    }

    public double getRatePerSecond() {
      return ratePerSecond;
    }

    public int getMaxBurstSeconds() {
      return maxBurstSeconds;
    }

    public int getValue() {
      if (value == null) {
        throw new IllegalStateException("expected value to be set");
      }
      return value;
    }

    private Type type;
    private double ratePerSecond;
    private int maxBurstSeconds;
    private Integer value;

    public RateLimit(Type type, double ratePerSecond, int maxBurstSeconds) {
      this.type = type;
      this.ratePerSecond = ratePerSecond;
      this.maxBurstSeconds = maxBurstSeconds;
    }

    public RateLimit(Type type, int value) {
      this.type = type;
      this.value = value;
    }
  }

  public static enum Type implements ConfigEnum {
    UPLOADPACK("uploadpack"),
    RESTAPI("restapi"),
    CONCURRENT_RESTAPI(GlobalQuotaSection.KEY_MAX_CONCURRENT_REST_API_CALLS_PER_USER);

    private final String name;

    Type(String type) {
      this.name = type;
    }

    @Override
    public String toConfigValue() {
      return name();
    }

    @Override
    public boolean matchConfigValue(String in) {
      return name().equalsIgnoreCase(in);
    }
  }

  private Table<Type, String, RateLimit> rateLimits;

  private AccountLimitsConfig(final Config c) {
    Set<String> groups = c.getSubsections(GROUP_SECTION);

    if (groups.isEmpty()) {
      return;
    }

    rateLimits = ArrayTable.create(Arrays.asList(Type.values()), groups);
    for (String groupName : groups) {
      parseRateLimit(c, GROUP_SECTION, groupName, UPLOADPACK);
      parseRateLimit(c, GROUP_SECTION, groupName, RESTAPI);
      parseConcurrentLimit(c, GROUP_SECTION, groupName);
    }

    parseRateLimit(c, GLOBAL_SECTION, null, UPLOADPACK);
    parseRateLimit(c, GLOBAL_SECTION, null, RESTAPI);
    parseConcurrentLimit(c, GLOBAL_SECTION, null);
  }

  void parseConcurrentLimit(Config c, String group, String groupName) {
    int value = c.getInt(group, groupName, CONCURRENT_RESTAPI.toConfigValue(), -1);
    if (value < 0) {
      return;
    }

    rateLimits.put(CONCURRENT_RESTAPI, groupName, new RateLimit(CONCURRENT_RESTAPI, value));
  }

  void parseRateLimit(Config c, String group, String groupName, Type type) {
    String name = type.toConfigValue();
    String value = c.getString(group, groupName, name);
    if (value == null) {
      return;
    }
    value = value.trim();

    Matcher m = PATTERN.matcher(value);
    if (!m.matches()) {
      log.error(
          "Invalid ''{}'' ratelimit configuration ''{}''; ignoring the configuration entry",
          name,
          value);
      return;
    }

    String digits = m.group(1);
    String unitName = m.group(2).trim();
    String storeCountString = m.group(3).trim();
    long burstCount;
    try {
      burstCount = Long.parseLong(storeCountString);
    } catch (NumberFormatException e) {
      log.error(
          "Invalid ''{}'' ratelimit store configuration ''{}''; ignoring the configuration entry",
          name,
          storeCountString);
      return;
    }

    TimeUnit inputUnit = TimeUnit.HOURS;
    double ratePerSecond;
    if (match(unitName, "s", "sec", "second")) {
      inputUnit = TimeUnit.SECONDS;
    } else if (match(unitName, "m", "min", "minute")) {
      inputUnit = TimeUnit.MINUTES;
    } else if (match(unitName, "h", "hr", "hour") || unitName.isEmpty()) {
      inputUnit = TimeUnit.HOURS;
    } else if (match(unitName, "d", "day")) {
      inputUnit = TimeUnit.DAYS;
    } else {
      logNotRateUnit(GROUP_SECTION, groupName, name, value);
      return;
    }
    try {
      ratePerSecond = 1.0D * Long.parseLong(digits) / TimeUnit.SECONDS.convert(1, inputUnit);
    } catch (NumberFormatException nfe) {
      logNotRateUnit(GROUP_SECTION, groupName, unitName, value);
      return;
    }

    int maxBurstSeconds = (int) (burstCount / ratePerSecond);
    rateLimits.put(type, groupName, new RateLimit(type, ratePerSecond, maxBurstSeconds));
  }

  private static boolean match(final String a, final String... cases) {
    for (final String b : cases) {
      if (b != null && b.equalsIgnoreCase(a)) {
        return true;
      }
    }
    return false;
  }

  private void logNotRateUnit(String section, String subsection, String name, String valueString) {
    if (subsection != null) {
      log.error(
          "Invalid rate unit value: {}.{}.{}={}; ignoring the configuration entry",
          section,
          subsection,
          name,
          valueString);
    } else {
      log.error(
          "Invalid rate unit value: {}.{}={}; ignoring the configuration entry",
          section,
          name,
          valueString);
    }
  }

  /**
   * @param type type of rate limit
   * @return map of rate limits per group name
   */
  Optional<Map<String, RateLimit>> getRatelimits(Type type) {
    return Optional.ofNullable(rateLimits).map(limits -> limits.row(type));
  }
}
