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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.ReceivePackInitializer;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.AbstractModule;

class Module extends AbstractModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), ProjectCreationValidationListener.class)
        .to(MaxRepositoriesQuotaValidator.class);
    DynamicSet.bind(binder(), ReceivePackInitializer.class)
        .to(MaxRepositorySizeQuota.class);
  }
}
