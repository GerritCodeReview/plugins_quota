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

package com.googlesource.gerrit.plugins.quota.usage;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.internal.UniqueAnnotations;

public class UsageModule extends AbstractModule {

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), UsageDataEventCreator.class);
    bind(Publisher.class).in(Scopes.SINGLETON);
    bind(PublisherScheduler.class).in(Scopes.SINGLETON);
    bind(LifecycleListener.class)
      .annotatedWith(UniqueAnnotations.create())
      .to(PublisherScheduler.class);
  }
}