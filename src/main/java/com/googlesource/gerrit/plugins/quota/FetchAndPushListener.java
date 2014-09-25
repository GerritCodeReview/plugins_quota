package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import com.googlesource.gerrit.plugins.quota.PersistentCounter.CounterException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class FetchAndPushListener implements PostReceiveHook, PreUploadHook {
  private static final Logger log = LoggerFactory
      .getLogger(FetchAndPushListener.class);

  private final ProjectNameResolver projectNameResolver;
  private final PersistentCounter fetchCounts;
  private final PersistentCounter pushCounts;

  @Inject
  public FetchAndPushListener(@Named(PersistentCounter.FETCH) PersistentCounter fetchCounts,
      @Named(PersistentCounter.PUSH) PersistentCounter pushCounts,
      ProjectNameResolver projectNameResolver) {
    this.fetchCounts = fetchCounts;
    this.pushCounts = pushCounts;
    this.projectNameResolver = projectNameResolver;
  }

  @Override
  public void onBeginNegotiateRound(UploadPack up,
      Collection<? extends ObjectId> wants, int cntOffered)
      throws ServiceMayNotContinueException {
  }

  @Override
  public void onEndNegotiateRound(UploadPack up,
      Collection<? extends ObjectId> wants, int cntCommon, int cntNotFound,
      boolean ready) throws ServiceMayNotContinueException {
  }

  @Override
  public void onSendPack(UploadPack up, Collection<? extends ObjectId> wants,
      Collection<? extends ObjectId> haves)
      throws ServiceMayNotContinueException {
    increment(up.getRepository(), fetchCounts);
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    increment(rp.getRepository(), pushCounts);
  }

  private void increment(Repository repo, PersistentCounter counts) {
    Project.NameKey p = projectNameResolver.projectName(repo);
    try {
      counts.increment(p);
    } catch (CounterException e) {
      log.error("can't increment counter " + counts.getKind() + " for project "
          + p.get());
    }
  }
}
