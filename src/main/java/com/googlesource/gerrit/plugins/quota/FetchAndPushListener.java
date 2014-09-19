package com.googlesource.gerrit.plugins.quota;

import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

import java.util.Collection;

public class FetchAndPushListener implements PostReceiveHook, PreUploadHook {

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
    Project.NameKey project =
        projectNameResolver.projectName(up.getRepository());
    fetchCounts.increment(project);
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    Project.NameKey project =
        projectNameResolver.projectName(rp.getRepository());
    pushCounts.increment(project);
  }

}
