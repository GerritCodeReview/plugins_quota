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

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

class PersistentCounter {
  private static final Logger log = LoggerFactory
      .getLogger(PersistentCounter.class);
  public static final String FETCH = "FETCH_COUNTS";
  public static final String PUSH = "PUSH_COUNTS";
  private static final String COUNTER_DATABASE = "COUNTER_DATA_BASE";
  private static final int MAX_RETRIES = 5;

  static Module module() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(
            creatorKey(FETCH));
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(
            creatorKey(PUSH));
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(
            RepoSizeEventCreator.class);
        bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create())
            .to(DataSourceProvider.class);
        bind(Key.get(DataSource.class, Names.named(COUNTER_DATABASE)))
            .toProvider(DataSourceProvider.class).in(SINGLETON);
      }

      private Key<UsageDataEventCreator> creatorKey(String kind) {
        Key<UsageDataEventCreator> pushCreatorKey =
            Key.get(new TypeLiteral<UsageDataEventCreator>() {},
                Names.named(kind));
        return pushCreatorKey;
      }

      @Provides
      @Singleton
      @Named(FETCH)
      PersistentCounter provideFetchCounter(
          @Named(COUNTER_DATABASE) Provider<DataSource> dataSource) {
        return new PersistentCounter(dataSource, "fetch");
      }

      @Provides
      @Singleton
      @Named(PUSH)
      PersistentCounter providePushCounter(
          @Named(COUNTER_DATABASE) Provider<DataSource> dataSource) {
        return new PersistentCounter(dataSource, "push");
      }

      @Provides
      @Singleton
      @Named(FETCH)
      UsageDataEventCreator provideFetchEventCreator(ProjectCache projectCache,
          @Named(FETCH) PersistentCounter counts) {
        return new FetchAndPushEventCreator(projectCache, counts,
            FetchAndPushEventCreator.FETCH_COUNT);
      }

      @Provides
      @Singleton
      @Named(PUSH)
      UsageDataEventCreator providePushEventCreator(ProjectCache projectCache,
          @Named(PUSH) PersistentCounter counts) {
        return new FetchAndPushEventCreator(projectCache, counts,
            FetchAndPushEventCreator.PUSH_COUNT);
      }
    };
  }

  private final DataSource dataSource;
  private final String kind;
  private volatile boolean initialized = false;

  public PersistentCounter(
      @Named(COUNTER_DATABASE) Provider<DataSource> dataSource, String kind) {
    this.kind = kind;
    this.dataSource = dataSource.get();
  }

  private Connection getConnection() throws SQLException {
    Connection conn = dataSource.getConnection();
    if (!initialized) {
      createTableIfNotExisting(conn);
      initialized = true;
    }
    return conn;
  }

  private void createTableIfNotExisting(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE IF NOT EXISTS " + kind
          + " (project VARCHAR(255) NOT NULL PRIMARY KEY HASH, "
          + "count BIGINT NOT NULL )");
    } catch (SQLException e) {
      throw new SQLException("error creating table '" + kind + "'", e);
    }
  }

  long getAndReset(Project.NameKey p) throws CounterException {
    try (Handle handle = new Handle()) {
      long count = handle.get(p);
      handle.delete(p);
      return count;
    } catch (SQLException e) {
      String msg = "error getting counts of project " + p.get();
      log.error(msg, e);
      throw new CounterException(msg, e);
    }
  }

  void increment(Project.NameKey p) throws CounterException {
    try (Handle handle = new Handle()) {
      long actual = handle.get(p);
      if (actual == 0) {
        // try insert
        handle.insertZeroIfRowNotExisting(p);
      }
      // try update
      for (int i = 0; !handle.incremented(p, actual) && i < MAX_RETRIES; i++) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          // do nothing
        }
        actual = handle.get(p);
      }
    } catch (SQLException e) {
      String msg = "error incrementing count of project " + p.get();
      log.error(msg, e);
      throw new CounterException(msg, e);
    }
  }

  public String getKind() {
    return kind;
  }

  class Handle implements AutoCloseable {

    private final Connection conn;

    public Handle() throws SQLException {
      this.conn = getConnection();
    }

    private long get(Project.NameKey p) throws SQLException {
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

    private void insertZeroIfRowNotExisting(NameKey p) {
      String insert = "INSERT INTO " + kind + " (project, count) VALUES (?, 0)";
      try (PreparedStatement stmt = conn.prepareStatement(insert)) {
        stmt.setString(1, p.get());
        stmt.executeUpdate();
      } catch (SQLException ex) {
        if (Integer.parseInt(ex.getSQLState()) == 23001) {
          // duprec -> ignore
        }
      }
    }

    private boolean incremented(NameKey p, long actual) throws SQLException {
      String update =
          "UPDATE " + kind
              + " SET count = count + 1 WHERE project = ? and count = ?";
      try (PreparedStatement stmt = conn.prepareStatement(update)) {
        stmt.setString(1, p.get());
        stmt.setLong(2, actual);
        return stmt.executeUpdate() == 1;
      }
    }

    private void delete(NameKey p) throws SQLException {
      String delete = "DELETE FROM " + kind + " WHERE project = ?";
      try (PreparedStatement stmt = conn.prepareStatement(delete)) {
        stmt.setString(1, p.get());
        stmt.executeUpdate();
      }
    }

    @Override
    public void close() throws SQLException {
      conn.close();
    }

  }

  public static class CounterException extends Exception {
    private static final long serialVersionUID = 1L;

    public CounterException(String msg, SQLException e) {
      super(msg, e);
    }

  }
}
