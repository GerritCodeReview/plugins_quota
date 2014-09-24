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

import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import com.googlesource.gerrit.plugins.quota.count.CountModule;
import com.googlesource.gerrit.plugins.quota.count.FetchAndPushListener;
import com.googlesource.gerrit.plugins.quota.usage.UsageDataEventCreator;
import com.googlesource.gerrit.plugins.quota.usage.UsageModule;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreUploadHook;

class Module extends AbstractModule {

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), ProjectCreationValidationListener.class)
        .to(MaxRepositoriesQuotaValidator.class);
    DynamicSet.bind(binder(), ReceivePackInitializer.class)
        .to(MaxRepositorySizeQuota.class);
    DynamicSet.bind(binder(), PostReceiveHook.class)
        .to(MaxRepositorySizeQuota.class);
    DynamicSet.bind(binder(), PostReceiveHook.class)
        .to(FetchAndPushListener.class);
    DynamicSet.bind(binder(), PreUploadHook.class)
        .to(FetchAndPushListener.class);
    DynamicSet.setOf(binder(), UsageDataEventCreator.class);
    install(MaxRepositorySizeQuota.module());
    install(new CountModule());
    install(new UsageModule());
    install(new RestApiModule() {
      @Override
      protected void configure() {
        get(PROJECT_KIND, "quota").to(GetQuota.class);
      }
    });
    bind(ProjectNameResolver.class).in(Scopes.SINGLETON);
  }
}
