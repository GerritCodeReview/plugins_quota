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

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.registration.DynamicSet;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

public class PublisherExceptionTest {

  private static final String CREATOR_NAME = "test-creator";
  private UsageDataPublishedListener listener;
  private UsageDataEventCreator creator;
  private Publisher classUnderTest;
  private Appender appender;
  private Capture<LoggingEvent> captor;

  @Before
  public void setupClassUnderTest() {
    listener = createMock(UsageDataPublishedListener.class);
    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    listeners.add(listener);

    creator = createMock(UsageDataEventCreator.class);
    expect(creator.getName()).andStubReturn(CREATOR_NAME);
    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add(creator);

    classUnderTest = new Publisher(listeners, creators);
  }

  @Before
  public void setupLogging() {
    captor = new Capture<LoggingEvent>();
    appender = createMock(Appender.class);
    appender.doAppend(capture(captor));
    expectLastCall().anyTimes();
    Logger.getRootLogger().addAppender(appender);
  }

  @Test
  public void testExceptionInCreatorIsLogged() {
    RuntimeException ex = new RuntimeException();
    expect(creator.create()).andStubThrow(ex);

    replay(listener, creator, appender);

    classUnderTest.run();

    verify(listener, creator, appender);

    assertTrue(captor.hasCaptured());
    LoggingEvent event = captor.getValue();
    assertEquals(Level.WARN, event.getLevel());
    assertTrue(((String)event.getMessage()).contains(CREATOR_NAME));
  }

  @Test
  public void testExceptionInListenerIsLogged() {
    RuntimeException ex = new RuntimeException();
    Event data = new UsageDataEvent(FetchAndPushEventCreator.FETCH_COUNT);
    expect(creator.create()).andStubReturn(data);

    listener.onUsageDataPublished(data);
    expectLastCall().andStubThrow(ex);

    replay(listener, creator, appender);

    classUnderTest.run();

    verify(listener, creator, appender);

    assertTrue(captor.hasCaptured());
    LoggingEvent event = captor.getValue();
    assertEquals(Level.WARN, event.getLevel());
  }

}
