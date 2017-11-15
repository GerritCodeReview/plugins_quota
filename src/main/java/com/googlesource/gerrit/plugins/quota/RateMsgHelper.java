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

public class RateMsgHelper {
  static final String RATE_LIMIT_TOKEN = "${rateLimit}";
  static final String BURSTS_LIMIT_TOKEN = "${burstsLimit}";

  // compare AccountLimitsConfig's constructor for default rate limits
  static final String[] DEFAULT_TEMPLATE_MSG_PARTS = {
    "Exceeded rate limit of " + RATE_LIMIT_TOKEN + " ",
    " requests/hour",
    " requests/hour (or idle time used up in bursts of max " + BURSTS_LIMIT_TOKEN + " requests)"
  };

  static String getDefaultTemplateMsg(String rateLimitTypeName) {
    return DEFAULT_TEMPLATE_MSG_PARTS[0] + rateLimitTypeName + DEFAULT_TEMPLATE_MSG_PARTS[1];
  }

  static String getDefaultTemplateMsgWithBursts(String rateLimitTypeName) {
    return DEFAULT_TEMPLATE_MSG_PARTS[0] + rateLimitTypeName + DEFAULT_TEMPLATE_MSG_PARTS[2];
  }

  public static final String UPLOADPACK_CONFIGURABLE_MSG_ANNOTATION = "uploadpackLimitExceededMsg";
  public static final String RESTAPI_CONFIGURABLE_MSG_ANNOTATION = "restapiLimitExceededMsg";

  private String messageFormatMsg;
  private String messageFormatMsgWithBursts;

  public RateMsgHelper(String rateLimitTypeName, String templateMsg) {
    messageFormatMsg = templateMsg == null ? getDefaultTemplateMsg(rateLimitTypeName) : templateMsg;
    messageFormatMsg = messageFormatMsg.replace(RATE_LIMIT_TOKEN, "{0,number,##.##}");
    messageFormatMsgWithBursts =
        templateMsg == null ? getDefaultTemplateMsgWithBursts(rateLimitTypeName) : templateMsg;
    messageFormatMsgWithBursts =
        messageFormatMsgWithBursts
            .replace(RATE_LIMIT_TOKEN, "{0,number,##.##}")
            .replace(BURSTS_LIMIT_TOKEN, "{1,number,###}");
  }

  public String getMessageFormatMsg() {
    return messageFormatMsg;
  }

  public String getMessageFormatMsgWithBursts() {
    return messageFormatMsgWithBursts;
  }
}
