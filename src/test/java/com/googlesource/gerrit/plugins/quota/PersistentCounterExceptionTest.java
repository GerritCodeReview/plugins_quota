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

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;

import com.googlesource.gerrit.plugins.quota.PersistentCounter.CounterException;

import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

public class PersistentCounterExceptionTest {

  private static final NameKey PROJECT = new Project.NameKey("p");
  private SQLException ex = new SQLException();
  private PersistentCounter classUnderTest;

  @Before
  public void setup() throws SQLException {
    Statement stmt = createNiceMock(Statement.class);
    Connection conn = createNiceMock(Connection.class);
    expect(conn.createStatement()).andStubReturn(stmt);
    expect(conn.prepareStatement(anyString())).andThrow(ex);
    DataSource ds = createNiceMock(DataSource.class);
    expect(ds.getConnection()).andStubReturn(conn);
    replay(stmt, conn, ds);

    classUnderTest = new PersistentCounter(ds,"test");
  }

  @Test
  public void testIncrementWrapsSQLException() throws Exception {
    try {
      classUnderTest.increment(PROJECT);
      fail();
    } catch (CounterException caught) {
      assertSame(ex, caught.getCause());
    }
  }

  @Test
  public void testGetAndResetWrapsSQLException() throws Exception {
    try {
      classUnderTest.getAllAndClear();
      fail();
    } catch (CounterException caught) {
      assertSame(ex, caught.getCause());
    }
  }

}
