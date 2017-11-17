// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class GetQuotas implements ChildCollection<ConfigResource, QuotaResource> {

  private final Provider<ListQuotas> list;
  private final DynamicMap<RestView<QuotaResource>> views;

  @Inject
  public GetQuotas(Provider<ListQuotas> list, DynamicMap<RestView<QuotaResource>> views) {
    this.list = list;
    this.views = views;
  }

  @Override
  public RestView<ConfigResource> list() throws ResourceNotFoundException, AuthException {
    return list.get();
  }

  @Override
  public QuotaResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<QuotaResource>> views() {
    return views;
  }
}
