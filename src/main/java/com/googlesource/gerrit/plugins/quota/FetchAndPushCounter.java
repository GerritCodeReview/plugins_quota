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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
class FetchAndPushCounter implements PostReceiveHook, PreUploadHook {
  static final String PUSH_COUNTS = "push_count";
  static final String FETCH_COUNTS = "fetch_count";

  static AbstractModule module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        TypeLiteral<ConcurrentMap<Project.NameKey, AtomicLong>> counterMapType =
            new TypeLiteral<ConcurrentMap<Project.NameKey, AtomicLong>>() {};
        bind(counterMapType).annotatedWith(Names.named(FETCH_COUNTS))
            .toInstance(new ConcurrentHashMap<Project.NameKey, AtomicLong>());
        bind(counterMapType).annotatedWith(Names.named(PUSH_COUNTS))
            .toInstance(new ConcurrentHashMap<Project.NameKey, AtomicLong>());
        bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create())
            .to(CounterStore.class);
      }
    };
  }

  private final ConcurrentMap<NameKey, AtomicLong> pushCounts;
  private final ConcurrentMap<NameKey, AtomicLong> fetchCounts;
  private final ProjectNameResolver projectNameResolver;

  @Inject
  FetchAndPushCounter(
      @Named(PUSH_COUNTS) ConcurrentMap<Project.NameKey, AtomicLong> pushCounts,
      @Named(FETCH_COUNTS) ConcurrentMap<Project.NameKey, AtomicLong> fetchCounts,
      ProjectNameResolver projectNameResolver) {
    this.pushCounts = pushCounts;
    this.fetchCounts = fetchCounts;
    this.projectNameResolver = projectNameResolver;
  }

  @Override
  public void onBeginNegotiateRound(UploadPack up,
      Collection<? extends ObjectId> wants, int cntOffered) {
  }

  @Override
  public void onEndNegotiateRound(UploadPack up,
      Collection<? extends ObjectId> wants, int cntCommon, int cntNotFound,
      boolean ready) {
  }

  @Override
  public void onSendPack(UploadPack up, Collection<? extends ObjectId> wants,
      Collection<? extends ObjectId> haves) {
    incrementCount(fetchCounts, up.getRepository());
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    incrementCount(pushCounts, rp.getRepository());
  }

  private void incrementCount(ConcurrentMap<NameKey, AtomicLong> counts,
      Repository repo) {
    Project.NameKey project = projectNameResolver.projectName(repo);
    AtomicLong old = counts.putIfAbsent(project, new AtomicLong(1));
    if (old != null) {
      old.incrementAndGet();
    }
  }
}
