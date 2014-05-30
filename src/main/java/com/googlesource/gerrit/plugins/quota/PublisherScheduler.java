package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import java.util.concurrent.TimeUnit;

public class PublisherScheduler implements LifecycleListener {

  private static final int INITIAL_DELAY = 1;
  private final WorkQueue workQueue;
  private final Publisher publisher;
  private final int period;

  @Inject
  PublisherScheduler(WorkQueue workQueue, Publisher publisher,  PluginConfigFactory cfg) {
    this.workQueue = workQueue;
    this.publisher = publisher;
    period = cfg.getFromGerritConfig("quota").getInt("publicationInterval", -1);
  }

  @Override
  public void start() {
    if(period < 1)
      return;
    workQueue.getDefaultQueue().scheduleWithFixedDelay(publisher, INITIAL_DELAY,
        period, TimeUnit.MINUTES);
  }

  @Override
  public void stop() {
  }

}
