package com.googlesource.gerrit.plugins.quota;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Module;
import com.google.inject.Singleton;

import java.util.concurrent.atomic.AtomicLong;

class PersistentCounter {
  static Module module() {
    return new CacheModule() {
      protected void configure() {
        DynamicSet.bind(binder(), UsageDataEventCreator.class).to(RepoSizeEventCreator.class);
      }
    };
  }

  private final LoadingCache<Project.NameKey, AtomicLong> counts;

  PersistentCounter(LoadingCache<Project.NameKey, AtomicLong> counts) {
    this.counts = counts;
  }

  long getAndReset(Project.NameKey p) {
    AtomicLong count = counts.getIfPresent(p);
    if (count != null) {
      return count.getAndSet(0);
    } else {
      return 0;
    }
  }

  void increment(Project.NameKey p) {
    counts.getUnchecked(p).incrementAndGet();
  }

  @Singleton
  private static class Loader extends CacheLoader<Project.NameKey, AtomicLong> {

    @Override
    public AtomicLong load(Project.NameKey project) {
      return new AtomicLong(0);
    }
  }

}
