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

import com.google.gerrit.reviewdb.client.Project;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Namespace {

  private final String namespace;
  private final boolean applies;

  Namespace(String namespace, Project.NameKey project) {
    String p = project.get();
    if ("?/*".equals(namespace) || namespace.endsWith("/?/*")) {
      String prefix = namespace.substring(0, namespace.length() - 3);
      Matcher m = Pattern.compile("^" + prefix + "([^/]+)/.*$").matcher(p);
      if (m.matches()) {
        namespace = prefix + m.group(1) + "/*";
      }
      this.applies = m.matches();
    } else if (namespace.endsWith("/*")) {
      this.applies = p.startsWith(
          namespace.substring(0, namespace.length() - 1));
    } else {
      this.applies = p.equals(namespace);
    }
    this.namespace = namespace;
  }

  public String get() {
    return namespace;
  }

  public boolean applies() {
    return applies;
  }

  public boolean matches(Project.NameKey project) {
    String p = project.get();
    if (namespace.endsWith("/*")) {
      return p.startsWith(
          namespace.substring(0, namespace.length() - 1));
    } else {
      return p.equals(namespace);
    }
  }
}
