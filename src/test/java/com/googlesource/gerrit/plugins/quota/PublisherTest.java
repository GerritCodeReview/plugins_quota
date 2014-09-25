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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.extensions.events.UsageDataPublishedListener;
import com.google.gerrit.extensions.events.UsageDataPublishedListener.Event;
import com.google.gerrit.extensions.registration.DynamicSet;

import org.junit.Test;

public class PublisherTest {

  @Test
  public void testAllEventsPropagatedToListener() throws Exception {
    Event e1 = new UsageDataEvent(FetchAndPushEventCreator.FETCH_COUNT);
    UsageDataEventCreator c1 = createMock(UsageDataEventCreator.class);
    expect(c1.create()).andStubReturn(e1);

    Event e2 = new UsageDataEvent(FetchAndPushEventCreator.PUSH_COUNT);
    UsageDataEventCreator c2 = createMock(UsageDataEventCreator.class);
    expect(c2.create()).andStubReturn(e2);

    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add(c1);
    creators.add(c2);

    UsageDataPublishedListener listener =
        createMock(UsageDataPublishedListener.class);
    listener.onUsageDataPublished(e1);
    expectLastCall();
    listener.onUsageDataPublished(e2);
    expectLastCall();

    replay(c1, c2, listener);
    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    listeners.add(listener);

    Publisher classUnderTest = new Publisher(listeners, creators);
    classUnderTest.run();

    verify(c1, c2, listener);
  }

  @Test
  public void testEventPropagatedToAllListeners() throws Exception {
    Event event = new UsageDataEvent(FetchAndPushEventCreator.FETCH_COUNT);
    UsageDataEventCreator creator = createMock(UsageDataEventCreator.class);
    expect(creator.create()).andStubReturn(event);
    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add(creator);

    UsageDataPublishedListener l1 =
        createMock(UsageDataPublishedListener.class);
    l1.onUsageDataPublished(event);
    expectLastCall();

    UsageDataPublishedListener l2 =
        createMock(UsageDataPublishedListener.class);
    l2.onUsageDataPublished(event);
    expectLastCall();

    replay(creator, l1, l2);

    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    listeners.add(l1);
    listeners.add(l2);

    Publisher classUnderTest = new Publisher(listeners, creators);
    classUnderTest.run();

    verify(creator, l1, l2);
  }

  @Test
  public void testNoEventsCreatedIfNoListenersRegistered() throws Exception {
    UsageDataEventCreator creator = createMock(UsageDataEventCreator.class);
    replay(creator);
    DynamicSet<UsageDataEventCreator> creators = DynamicSet.emptySet();
    creators.add(creator);

    DynamicSet<UsageDataPublishedListener> listeners = DynamicSet.emptySet();
    Publisher classUnderTest = new Publisher(listeners, creators);
    classUnderTest.run();

    verify(creator);
  }

}
