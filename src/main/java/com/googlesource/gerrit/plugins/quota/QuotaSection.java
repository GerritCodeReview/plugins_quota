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

import com.google.common.base.Objects;

import org.eclipse.jgit.lib.Config;

import java.util.Set;

public class QuotaSection {
  public static final String QUOTA = "quota";
  public static final String KEY_MAX_PROJECTS = "maxProjects";

  private final Config cfg;
  private final String namespace;
  private final Namespace resolvedNamespace;

  QuotaSection(Config cfg, String namespace, String resolvedNamespace) {
    this.cfg = cfg;
    this.namespace = namespace;
    this.resolvedNamespace = new Namespace(resolvedNamespace);
  }

  public Namespace getNamespace() {
    return resolvedNamespace;
  }

  public String getString(String name) {
    return cfg.getString(QUOTA, namespace, name);
  }

  public String getString(String name, String defaultValue) {
    if (defaultValue == null) {
      return cfg.getString(QUOTA, namespace, name);
    } else {
      return Objects.firstNonNull(cfg.getString(QUOTA, namespace, name), defaultValue);
    }
  }

  public String[] getStringList(String name) {
    return cfg.getStringList(QUOTA, namespace, name);
  }

  public int getInt(String name, int defaultValue) {
    return cfg.getInt(QUOTA, namespace, name, defaultValue);
  }

  public long getLong(String name, long defaultValue) {
    return cfg.getLong(QUOTA, namespace, name, defaultValue);
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    return cfg.getBoolean(QUOTA, namespace, name, defaultValue);
  }

  public <T extends Enum<?>> T getEnum(String name, T defaultValue) {
    return cfg.getEnum(QUOTA, namespace, name, defaultValue);
  }

  public <T extends Enum<?>> T getEnum(T[] all, String name, T defaultValue) {
    return cfg.getEnum(all, QUOTA, namespace, name, defaultValue);
  }

  public Set<String> getNames() {
    return cfg.getNames(QUOTA, namespace);
  }
}
