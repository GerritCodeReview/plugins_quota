// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;
import static com.google.gerrit.server.quota.QuotaResponse.Status.ERROR;
import static com.google.gerrit.server.quota.QuotaResponse.Status.NO_OP;
import static com.google.gerrit.server.quota.QuotaResponse.Status.OK;
import static com.googlesource.gerrit.plugins.quota.QuotaSection.KEY_MAX_REPO_SIZE;
import static com.googlesource.gerrit.plugins.quota.QuotaSection.QUOTA;
import static org.mockito.Mockito.when;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.quota.QuotaRequestContext;
import com.google.gerrit.server.quota.QuotaResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.ServletException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MaxRepositorySizeQuotaTest {

  @Mock QuotaFinder quotaFinder;
  @Mock ProjectCache projectCache;
  @Mock QuotaRequestContext quotaRequestContext;
  @Mock MaxRepositorySizeQuota.Loader repoSizeLoader;
  MaxRepositorySizeQuota maxRepositorySizeQuota;

  private static final Project.NameKey PROJECT_NAME = Project.nameKey("foo");

  @Before
  public void setUp() throws IOException, ServletException {
    when(quotaRequestContext.project()).thenReturn(Optional.of(PROJECT_NAME));
    LoadingCache<Project.NameKey, AtomicLong> repoSizeCache =
        CacheBuilder.newBuilder().build(repoSizeLoader);
    maxRepositorySizeQuota = new MaxRepositorySizeQuota(quotaFinder, repoSizeCache, projectCache);
  }

  @Test
  public void dryRunOKWhenRequestingLessThanAvailableQuota() throws IOException {
    long currentRepoSize = 1;
    long maxRepoSize = 3;
    long requestQuotaSize = 1;
    setupQuotas(currentRepoSize, maxRepoSize);

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.dryRun(REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status()).isEqualTo(OK);
  }

  @Test
  public void dryRunOKWhenRequestingExactlyAvailableQuota() throws IOException {
    long currentRepoSize = 1;
    long maxRepoSize = 3;
    long requestQuotaSize = 2;
    setupQuotas(currentRepoSize, maxRepoSize);

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.dryRun(REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status()).isEqualTo(OK);
  }

  @Test
  public void dryRunErrorWhenRequestingTooMuchQuota() throws IOException {
    long currentRepoSize = 1;
    long maxRepoSize = 3;
    long requestQuotaSize = 4;
    setupQuotas(currentRepoSize, maxRepoSize);

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.dryRun(REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status()).isEqualTo(ERROR);
  }

  @Test
  public void dryRunNoopWhenQuotaIsNotEnforcedForProject() {
    long requestQuotaSize = 1;

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.dryRun(REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status()).isEqualTo(NO_OP);
  }

  @Test
  public void dryRunOKOnNonExistingProject() throws IOException {
    long currentRepoSize = 1;
    long maxRepoSize = 3;
    long requestQuotaSize = 1;
    setupQuotas(currentRepoSize, maxRepoSize);
    when(repoSizeLoader.load(PROJECT_NAME))
        .thenThrow(new RepositoryNotFoundException("/var/gerrit/" + PROJECT_NAME));

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.dryRun(REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status().isOk()).isTrue();
  }

  @Test
  public void requestTokensNotOKOnNonExistingProject() throws IOException {
    long currentRepoSize = 1;
    long maxRepoSize = 3;
    long requestQuotaSize = 1;
    setupQuotas(currentRepoSize, maxRepoSize);
    when(repoSizeLoader.load(PROJECT_NAME))
        .thenThrow(new RepositoryNotFoundException("/var/gerrit/" + PROJECT_NAME));

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.requestTokens(
            REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status().isOk()).isFalse();
  }

  @Test
  public void dryRunNotOKWhenOtherExceptionsOccur() throws IOException {
    long currentRepoSize = 1;
    long maxRepoSize = 3;
    long requestQuotaSize = 1;
    setupQuotas(currentRepoSize, maxRepoSize);
    when(repoSizeLoader.load(PROJECT_NAME)).thenThrow(new IOException("Some exception occurred"));

    QuotaResponse quotaResponse =
        maxRepositorySizeQuota.dryRun(REPOSITORY_SIZE_GROUP, quotaRequestContext, requestQuotaSize);

    assertThat(quotaResponse.status().isOk()).isFalse();
  }

  private void setupQuotas(long currentRepoSize, long maxRepoSize) throws IOException {
    Config config = new Config();
    config.setLong(QUOTA, PROJECT_NAME.get(), KEY_MAX_REPO_SIZE, maxRepoSize);
    when(repoSizeLoader.load(PROJECT_NAME)).thenReturn(new AtomicLong(currentRepoSize));

    when(quotaFinder.firstMatching(PROJECT_NAME))
        .thenReturn(new QuotaSection(config, PROJECT_NAME.get()));
  }
}
