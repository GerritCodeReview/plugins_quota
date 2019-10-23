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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.registration.DynamicSet;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PublisherExceptionTest {

  private static final String CREATOR_NAME = "test-creator";
  private UsageDataPublishedListener listener;
  private UsageDataEventCreator creator;
  private Publisher classUnderTest;
  private Appender appender;
  private ArgumentCaptor<LoggingEvent> captor;
  private DynamicSet<UsageDataPublishedListener> listeners;
  private DynamicSet<UsageDataEventCreator> creators;

  @Before
  public void setupClassUnderTest() {
    listener = mock(UsageDataPublishedListener.class);
    listeners = DynamicSet.emptySet();
    listeners.add("quota", listener);

    creator = mock(UsageDataEventCreator.class);
    when(creator.getName()).thenReturn(CREATOR_NAME);
    creators = DynamicSet.emptySet();
    creators.add("quota", creator);

    classUnderTest = new Publisher(listeners, creators);
  }

  @Before
  public void setupLogging() {
    captor = ArgumentCaptor.forClass(LoggingEvent.class);
    appender = mock(Appender.class);
    // appender.doAppend(captor.capture());
    Logger.getRootLogger().addAppender(appender);
  }

  @Test
  public void testExceptionInCreatorIsLogged() {
    RuntimeException ex = new RuntimeException();
    when(creator.create()).thenThrow(ex);

    classUnderTest.run();

    verify(appender).doAppend(captor.capture());
    LoggingEvent event = captor.getValue();
    assertEquals(Level.WARN, event.getLevel());
    assertTrue(((String) event.getMessage()).contains(CREATOR_NAME));
  }

  @Test
  public void testDataFromGoodCreatorIsPropagated() {
    RuntimeException ex = new RuntimeException();
    when(creator.create()).thenThrow(ex);

    UsageDataEventCreator good = mock(UsageDataEventCreator.class);
    Event data = new UsageDataEvent(null);
    when(good.create()).thenReturn(data);
    creators.add("quota", good);

    classUnderTest.run();

    verify(listener).onUsageDataPublished(data);
  }

  @Test
  public void testExceptionInListenerIsLogged() {
    RuntimeException ex = new RuntimeException();
    Event data = new UsageDataEvent(null);
    when(creator.create()).thenReturn(data);
    doThrow(ex).when(listener).onUsageDataPublished(data);

    classUnderTest.run();

    verify(listener).onUsageDataPublished(data);

    verify(appender).doAppend(captor.capture());
    LoggingEvent event = captor.getValue();
    assertEquals(Level.WARN, event.getLevel());
  }

  @Test
  public void testIsPropagatedToGoodListener() {
    RuntimeException ex = new RuntimeException();
    Event data = new UsageDataEvent(null);
    when(creator.create()).thenReturn(data);
    doThrow(ex).when(listener).onUsageDataPublished(data);

    UsageDataPublishedListener good = mock(UsageDataPublishedListener.class);
    listeners.add("quota", good);

    classUnderTest.run();

    verify(listener).onUsageDataPublished(data);
    verify(good).onUsageDataPublished(data);
  }
}
