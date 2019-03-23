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

import static com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type.RESTAPI;
import static com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type.UPLOADPACK;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import java.text.MessageFormat;
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

public class AccountLimitsConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern PATTERN =
      Pattern.compile("^\\s*(\\d+)\\s*/\\s*(.*)\\s*burst\\s*(\\d+)$");
  static final String GROUP_SECTION = "group";
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

    private Type type;
    private double ratePerSecond;
    private int maxBurstSeconds;

    public RateLimit(Type type, double ratePerSecond, int maxBurstSeconds) {
      this.type = type;
      this.ratePerSecond = ratePerSecond;
      this.maxBurstSeconds = maxBurstSeconds;
    }
  }

  public static enum Type implements ConfigEnum {
    UPLOADPACK,
    RESTAPI;

    @Override
    public String toConfigValue() {
      return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matchConfigValue(String in) {
      return name().equalsIgnoreCase(in);
    }
  }

  private Table<Type, String, RateLimit> rateLimits;

  private AccountLimitsConfig(final Config c) {
    Set<String> groups = c.getSubsections(GROUP_SECTION);
    if (groups.size() == 0) {
      return;
    }
    rateLimits = ArrayTable.create(Arrays.asList(Type.values()), groups);
    for (String groupName : groups) {
      parseRateLimit(c, groupName, UPLOADPACK);
      parseRateLimit(c, groupName, RESTAPI);
    }
  }

  void parseRateLimit(Config c, String groupName, Type type) {
    String name = type.toConfigValue();
    String value = c.getString(GROUP_SECTION, groupName, name);
    if (value == null) {
      return;
    }
    value = value.trim();

    Matcher m = PATTERN.matcher(value);
    if (!m.matches()) {
      logger.atSevere().log("Invalid '%s' ratelimit configuration '%s'; ignoring the configuration entry", name, value);
      return;
    }

    String digits = m.group(1);
    String unitName = m.group(2).trim();
    String storeCountString = m.group(3).trim();
    long burstCount;
    try {
      burstCount = Long.parseLong(storeCountString);
    } catch (NumberFormatException e) {
      logger.atSevere().log("Invalid '%s' ratelimit store configuration '%s'; ignoring the configuration entry", name, storeCountString);
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
      logger.atSevere().log(
                  "Invalid rate unit value: %s.%s.%s=%s; ignoring the configuration entry",
                  section, subsection, name, valueString);
    } else {
      logger.atSevere().log(
                  "Invalid rate unit value: %s.%s=%s; ignoring the configuration entry",
                  section, name, valueString);
    }
  }

  /**
   * @param type type of rate limit
   * @return map of rate limits per group name
   */
  Optional<Map<String, RateLimit>> getRatelimits(Type type) {
    if (rateLimits != null) {
      return Optional.ofNullable(rateLimits.row(type));
    }
    return Optional.empty();
  }
}
