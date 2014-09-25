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

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;

import javax.sql.DataSource;

@Singleton
public class DataSourceProvider implements Provider<DataSource>, LifecycleListener {

  private static final int MAX_ACTIVE = 8;

  private static final Logger log = LoggerFactory.getLogger(DataSourceProvider.class);

  private final File dataDir;

  @Inject
  public DataSourceProvider(@PluginData File dataDir) {
    this.dataDir = dataDir;
  }

  private BasicDataSource ds;

  @Override
  public void start() {
    // do nothing
  }

  @Override
  public synchronized DataSource get() {
    if (ds == null) {
      File db = new File(dataDir, "counts.db").getAbsoluteFile();
      String url = "jdbc:h2:" + db.toURI().toString();

      ds = new BasicDataSource();
      ds.setDriverClassName("org.h2.Driver");
      ds.setMaxActive(MAX_ACTIVE);
      ds.setUrl(url);
    }
    return ds;
  }

  @Override
  public void stop() {
    if (ds != null) {
      try {
        ds.close();
      } catch (SQLException e) {
        log.error("error closing data source", e);
      }
    }
  }
}
