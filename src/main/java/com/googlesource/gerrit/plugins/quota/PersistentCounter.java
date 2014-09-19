package com.googlesource.gerrit.plugins.quota;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class PersistentCounter {
  public static final String FETCH = "FETCH_COUNTS";
  public static final String PUSH = "PUSH_COUNTS";

  static Module module() {
    return new CacheModule() {
      protected void configure() {
        persist(FETCH, Project.NameKey.class, AtomicLong.class).loader(
            Loader.class).expireAfterWrite(Integer.MAX_VALUE, TimeUnit.DAYS);
        persist(PUSH, Project.NameKey.class, AtomicLong.class).loader(
            Loader.class).expireAfterWrite(Integer.MAX_VALUE, TimeUnit.DAYS);
      }

      @Provides @Singleton @Named(FETCH)
      PersistentCounter provideFetchCounter(
          @Named(FETCH) LoadingCache<Project.NameKey, AtomicLong> counts) {
        return new PersistentCounter(counts);
      }

      @Provides @Singleton @Named(PUSH)
      PersistentCounter providePushCounter(
          @Named(PUSH) LoadingCache<Project.NameKey, AtomicLong> counts) {
        return new PersistentCounter(counts);
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
