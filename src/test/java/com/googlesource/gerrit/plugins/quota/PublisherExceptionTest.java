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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
  private DynamicSet<UsageDataPublishedListener> listeners;
  private DynamicSet<UsageDataEventCreator> creators;

  @Before
  public void setupClassUnderTest() {
    listener = createMock(UsageDataPublishedListener.class);
    listeners = DynamicSet.emptySet();
    listeners.add(listener);

    creator = createMock(UsageDataEventCreator.class);
    expect(creator.getName()).andStubReturn(CREATOR_NAME);
    creators = DynamicSet.emptySet();
    creators.add(creator);

    classUnderTest = new Publisher(listeners, creators);
  }

  @Before
  public void setupLogging() {
    captor = new Capture<>();
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
  public void testDataFromGoodCreatorIsPropagated() {
    RuntimeException ex = new RuntimeException();
    expect(creator.create()).andStubThrow(ex);

    UsageDataEventCreator good = createMock(UsageDataEventCreator.class);
    Event data = new UsageDataEvent(null);
    expect(good.create()).andStubReturn(data);
    creators.add(good);

    listener.onUsageDataPublished(data);
    expectLastCall();

    replay(listener, creator, good, appender);

    classUnderTest.run();

    verify(listener, creator, appender);
  }

  @Test
  public void testExceptionInListenerIsLogged() {
    RuntimeException ex = new RuntimeException();
    Event data = new UsageDataEvent(null);
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

  @Test
  public void testIsPropagatedToGoodListener() {
    RuntimeException ex = new RuntimeException();
    Event data = new UsageDataEvent(null);
    expect(creator.create()).andStubReturn(data);

    listener.onUsageDataPublished(data);
    expectLastCall().andStubThrow(ex);

    UsageDataPublishedListener good = createMock(UsageDataPublishedListener.class);
    good.onUsageDataPublished(data);
    listeners.add(good);

    replay(listener, good, creator, appender);

    classUnderTest.run();

    verify(listener, good, creator, appender);
  }

}
