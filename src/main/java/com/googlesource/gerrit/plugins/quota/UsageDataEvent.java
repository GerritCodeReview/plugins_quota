// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.events.UsageDataPublishedListener.Data;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.MetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

class UsageDataEvent implements Event {

  private final Timestamp timestamp;
  private final MetaData metaData;
  private final List<Data> data;

  UsageDataEvent(MetaData metaData) {
    this.metaData = metaData;
    timestamp = new Timestamp(System.currentTimeMillis());
    data = new ArrayList<>();
  }

  void addData(final long value, final String projectName) {
    Data dataRow =
        new Data() {

          @Override
          public long getValue() {
            return value;
          }

          @Override
          public String getProjectName() {
            return projectName;
          }
        };

    data.add(dataRow);
  }

  @Override
  public MetaData getMetaData() {
    return metaData;
  }

  @Override
  public Timestamp getInstant() {
    return timestamp;
  }

  @Override
  public List<Data> getData() {
    return data;
  }
}
