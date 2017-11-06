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

import com.googlesource.gerrit.plugins.quota.AccountLimitsConfig.Type;

public class RateMsgHelper {
  public static final String UPLOADPACK_CONFIGURABLE_MSG_ANNOTATION = "uploadpackLimitExceededMsg";
  public static final String RESTAPI_CONFIGURABLE_MSG_ANNOTATION = "restapiLimitExceededMsg";
  private static final String RATE_LIMIT_TOKEN = "${rateLimit}";
  private static final String BURSTS_LIMIT_TOKEN = "${burstsLimit}";
  private static final String RATE_LIMIT_FORMAT_DOUBLE = "{0,number,##.##}";
  private static final String RATE_LIMIT_FORMAT_INT = "{1,number,###}";
  private static final String UPLOADPACK_INLINE_NAME = "fetch";
  private static final String RESTAPI_INLINE_NAME = "REST API";

  // compare AccountLimitsConfig's constructor for default rate limits
  private static String getDefaultTemplateMsg(String rateLimitTypeName) {
    return "Exceeded rate limit of "
        + RATE_LIMIT_TOKEN
        + " "
        + rateLimitTypeName
        + " requests/hour";
  }

  private static String getDefaultTemplateMsgWithBursts(String rateLimitTypeName) {
    return "Exceeded rate limit of "
        + RATE_LIMIT_TOKEN
        + " "
        + rateLimitTypeName
        + " requests/hour (or idle time used up in bursts of max "
        + BURSTS_LIMIT_TOKEN
        + " requests)";
  }

  private String messageFormatMsg;
  private String messageFormatMsgWithBursts;

  public RateMsgHelper(Type limitsConfigType, String templateMsg) {
    String rateLimitTypeName =
        limitsConfigType == Type.UPLOADPACK ? UPLOADPACK_INLINE_NAME : RESTAPI_INLINE_NAME;
    messageFormatMsg = templateMsg == null ? getDefaultTemplateMsg(rateLimitTypeName) : templateMsg;
    messageFormatMsg = messageFormatMsg.replace(RATE_LIMIT_TOKEN, RATE_LIMIT_FORMAT_DOUBLE);
    messageFormatMsgWithBursts =
        templateMsg == null ? getDefaultTemplateMsgWithBursts(rateLimitTypeName) : templateMsg;
    messageFormatMsgWithBursts =
        messageFormatMsgWithBursts
            .replace(RATE_LIMIT_TOKEN, RATE_LIMIT_FORMAT_DOUBLE)
            .replace(BURSTS_LIMIT_TOKEN, RATE_LIMIT_FORMAT_INT);
  }

  public String getMessageFormatMsg() {
    return messageFormatMsg;
  }

  public String getMessageFormatMsgWithBursts() {
    return messageFormatMsgWithBursts;
  }
}
