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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class PersistentCounter {
  private static final Logger log = LoggerFactory
      .getLogger(PersistentCounter.class);
  public static final String FETCH = "FETCH_COUNTS";
  public static final String PUSH = "PUSH_COUNTS";

  static Module module() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(creatorKey(FETCH));
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(creatorKey(PUSH));
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(RepoSizeEventCreator.class);
      }

      private Key<UsageDataEventCreator> creatorKey(String kind) {
        Key<UsageDataEventCreator> pushCreatorKey =
            Key.get(new TypeLiteral<UsageDataEventCreator>() {},
                Names.named(kind));
        return pushCreatorKey;
      }

      @Provides @Singleton @Named(FETCH)
      PersistentCounter provideFetchCounter(@PluginData File dataDir) {
        return new PersistentCounter(dataDir, "fetch");
      }

      @Provides @Singleton  @Named(PUSH)
      PersistentCounter providePushCounter(@PluginData File dataDir) {
        return new PersistentCounter(dataDir, "push");
      }

      @Provides @Singleton @Named(FETCH)
      UsageDataEventCreator provideFetchEventCreator(ProjectCache projectCache,
          @Named(FETCH) PersistentCounter counts) {
        return new FetchAndPushEventCreator(projectCache, counts, FetchAndPushEventCreator.FETCH_COUNT);
      }

      @Provides @Singleton @Named(PUSH)
      UsageDataEventCreator providePushEventCreator(ProjectCache projectCache,
          @Named(PUSH) PersistentCounter counts) {
        return new FetchAndPushEventCreator(projectCache, counts, FetchAndPushEventCreator.PUSH_COUNT);
      }
    };
  }

  private final BasicDataSource dataSource;

  private String kind;

  public PersistentCounter(File dataDir, String kind) {
    this.kind = kind;
    File db = new File(dataDir, "counts.db").getAbsoluteFile();
    String url = "jdbc:h2:" + db.toURI().toString();

    dataSource = new BasicDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
//    dataSource.setMaxActive(10);
    dataSource.setUrl(url);
    try (Connection conn = dataSource.getConnection()) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + kind
            + " (project VARCHAR(255) NOT NULL PRIMARY KEY HASH"
            + ",count BIGINT NOT NULL )");
      }
    } catch (SQLException e) {
      log.error("error creating table 'counts'", e);
    }
  }

  long getAndReset(Project.NameKey p) {
    try (Connection conn = dataSource.getConnection()) {
      long count = get(conn, p);
      delete(conn, p);
      return count;
    } catch (SQLException e) {
      log.error("error getting counts of project " + p.get(), e);
      return 0l;
    }
  }

  private void delete(Connection conn, NameKey p) throws SQLException {
    String delete = "DELETE FROM " + kind + " WHERE project = ?";
    try (PreparedStatement stmt = conn.prepareStatement(delete)) {
      stmt.setString(1, p.get());
      stmt.executeUpdate();
    }
  }

  private long get(Connection conn, Project.NameKey p) throws SQLException {
    String query = "SELECT count FROM " + kind + " WHERE project = ?";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, p.get());
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0l;
      }
    }
  }

  void increment(Project.NameKey p) {
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      if (exists(conn, p)) {
        incrementCount(conn, p);
      } else {
        insertOne(conn, p);
      }
      conn.commit();
    } catch (SQLException e) {
      log.error("error incrementing count of project " + p.get(), e);
    }
  }

  private boolean exists(Connection conn, Project.NameKey p)
      throws SQLException {
    try (PreparedStatement select =
        conn.prepareStatement("SELECT count FROM " + kind
            + " WHERE project = ? FOR UPDATE")) {
      select.setString(1, p.get());
      try (ResultSet rs = select.executeQuery()) {
        return rs.next();
      }
    }
  }

  private void insertOne(Connection conn, NameKey p) throws SQLException {
    String insert = "INSERT INTO " + kind + " (project, count) VALUES (?, 1)";
    try (PreparedStatement stmt = conn.prepareStatement(insert)) {
      stmt.setString(1, p.get());
      stmt.executeUpdate();
    }
  }

  private void incrementCount(Connection conn, NameKey p) throws SQLException {
    String update = "UPDATE " + kind + " SET count = count + 1 WHERE project = ?";
    try (PreparedStatement stmt = conn.prepareStatement(update)) {
      stmt.setString(1, p.get());
      stmt.executeUpdate();
    }
  }

  public void close() {
    try {
      dataSource.close();
    } catch (SQLException e) {
      log.error("error closing data source", e);
    }
  }

}
