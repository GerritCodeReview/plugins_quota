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
  static final String UPLOADPACK_CONFIGURABLE_MSG_ANNOTATION = "uploadpackLimitExceededMsg";
  private static final String RATE_LIMIT_TOKEN = "${rateLimit}";
  private static final String RATE_LIMIT_FORMAT_DOUBLE = "{0,number,##.##}";
  private static final String UPLOADPACK_DEFAULT_TEMPLATE_MSG =
      "Exceeded rate limit of " + RATE_LIMIT_TOKEN + " fetch requests/hour";

  private String messageFormatMsg;

  public RateMsgHelper(String templateMsg) {
    messageFormatMsg = templateMsg == null ? UPLOADPACK_DEFAULT_TEMPLATE_MSG : templateMsg;
    messageFormatMsg = messageFormatMsg.replace(RATE_LIMIT_TOKEN, RATE_LIMIT_FORMAT_DOUBLE);
  }

  public String getMessageFormatMsg() {
    return messageFormatMsg;
  }
}