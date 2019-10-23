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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.registration.DynamicSet;
import org.junit.Test;

public class PublisherTest {

  @Test
  public void testAllEventsPropagatedToListener() throws Exception {
    Event e1 = new UsageDataEvent(null);
    UsageDataEventCreator c1 = mock(UsageDataEventCreator.class);
    when(c1.create()).thenReturn(e1);

    Event e2 = new UsageDataEvent(null);
    UsageDataEventCreator c2 = mock(UsageDataEventCreator.class);
    when(c2.create()).thenReturn(e2);

    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add("quota", c1);
    creators.add("quota", c2);

    UsageDataPublishedListener listener = mock(UsageDataPublishedListener.class);

    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    listeners.add("quota", listener);

    Publisher classUnderTest = new Publisher(listeners, creators);
    classUnderTest.run();

    verify(listener).onUsageDataPublished(e1);
    verify(listener).onUsageDataPublished(e2);
  }

  @Test
  public void testEventPropagatedToAllListeners() throws Exception {
    Event event = new UsageDataEvent(null);
    UsageDataEventCreator creator = mock(UsageDataEventCreator.class);
    when(creator.create()).thenReturn(event);
    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add("quota", creator);

    UsageDataPublishedListener l1 = mock(UsageDataPublishedListener.class);

    UsageDataPublishedListener l2 = mock(UsageDataPublishedListener.class);

    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    listeners.add("quota", l1);
    listeners.add("quota", l2);

    Publisher classUnderTest = new Publisher(listeners, creators);
    classUnderTest.run();

    verify(l1).onUsageDataPublished(event);
    verify(l2).onUsageDataPublished(event);
  }

  @Test
  public void testNoEventsCreatedIfNoListenersRegistered() throws Exception {
    UsageDataEventCreator creator = mock(UsageDataEventCreator.class);
    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add("quota", creator);

    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    Publisher classUnderTest = new Publisher(listeners, creators);
    classUnderTest.run();
  }
}
