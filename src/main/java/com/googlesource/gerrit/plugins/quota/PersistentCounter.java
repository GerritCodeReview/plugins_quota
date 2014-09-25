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
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
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
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

class PersistentCounter {
  private static final Logger log = LoggerFactory
      .getLogger(PersistentCounter.class);
  public static final String FETCH = "FETCH_COUNTS";
  public static final String PUSH = "PUSH_COUNTS";
  private static final String COUNTER_DATABASE = "COUNTER_DATA_BASE";

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
          @Named(COUNTER_DATABASE) DataSource dataSource) throws SQLException {
        return new PersistentCounter(dataSource, "fetch");
      }

      @Provides
      @Singleton
      @Named(PUSH)
      PersistentCounter providePushCounter(
          @Named(COUNTER_DATABASE) DataSource dataSource) throws SQLException {
        return new PersistentCounter(dataSource, "push");
      }

      @Provides
      @Singleton
      @Named(FETCH)
      UsageDataEventCreator provideFetchEventCreator(
          @Named(FETCH) PersistentCounter counts) {
        return new FetchAndPushEventCreator(counts,
            FetchAndPushEventCreator.FETCH_COUNT);
      }

      @Provides
      @Singleton
      @Named(PUSH)
      UsageDataEventCreator providePushEventCreator(
          @Named(PUSH) PersistentCounter counts) {
        return new FetchAndPushEventCreator(counts,
            FetchAndPushEventCreator.PUSH_COUNT);
      }
    };
  }

  private final DataSource dataSource;
  private final String kind;

  PersistentCounter(DataSource dataSource, String kind) throws SQLException {
    this.kind = kind;
    this.dataSource = dataSource;
    try (Connection conn = dataSource.getConnection()) {
      createTableIfNotExisting(conn);
    }
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

  Map<String, Long> getAllAndClear() throws CounterException {
    try (Handle c = new Handle()) {
      Map<String, Long> result = c.getAll();
      c.setAllToZero();
      return result;
    } catch (SQLException e) {
      String msg = "error getting counts of projects";
      log.error(msg, e);
      throw new CounterException(msg, e);
    }
  }

  void increment(Project.NameKey p) throws CounterException {
    try (Handle c = new Handle()) {
      if (!c.increment(p)) {
        // does not yet exist
        if (!c.insertOne(p)) {
          // concurrently inserted
          if (!c.increment(p)) {
            // should never happen
            String msg = "error incrementing count of project " + p.get();
            log.error(msg);
            throw new CounterException(msg);
          }
        }
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
      this.conn = dataSource.getConnection();
    }

    public void setAllToZero() throws SQLException {
      String update = "UPDATE " + kind + " SET count = 0";
      try (PreparedStatement stmt = conn.prepareStatement(update)) {
        stmt.executeUpdate();
      }
    }

    public Map<String, Long> getAll() throws SQLException {
      Map<String, Long> result = new HashMap<>();
      String query = "SELECT project, count FROM " + kind + " FOR UPDATE";
      try (PreparedStatement stmt = conn.prepareStatement(query)) {
        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            result.put(rs.getString(1), rs.getLong(2));
          }
        }
      }
      return result;
    }

    private boolean insertOne(Project.NameKey p) throws SQLException {
      String insert = "INSERT INTO " + kind + " (project, count) VALUES (?, 1)";
      try (PreparedStatement stmt = conn.prepareStatement(insert)) {
        stmt.setString(1, p.get());
        stmt.executeUpdate();
        return true;
      } catch (SQLException ex) {
        if (Integer.parseInt(ex.getSQLState()) == 23001) {
          // duprec -> ignore
          return false;
        } else {
          throw ex;
        }
      }
    }

    public boolean increment(Project.NameKey p) throws SQLException {
      String update =
          "UPDATE " + kind
              + " SET count = count + 1 WHERE project = ?";
      try (PreparedStatement stmt = conn.prepareStatement(update)) {
        stmt.setString(1, p.get());
        return stmt.executeUpdate() == 1;
      }
    }

    @Override
    public void close() throws SQLException {
      conn.close();
    }

  }

  public static class CounterException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CounterException(String msg, SQLException e) {
      super(msg, e);
    }

    public CounterException(String msg) {
      super(msg);
    }

  }
}
