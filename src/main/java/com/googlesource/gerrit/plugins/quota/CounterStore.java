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

import static com.googlesource.gerrit.plugins.quota.FetchAndPushCounter.FETCH_COUNTS;
import static com.googlesource.gerrit.plugins.quota.FetchAndPushCounter.PUSH_COUNTS;

import com.google.common.base.Charsets;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class CounterStore implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(CounterStore.class);
  private static final String FETCH_COUNTS_FILE = "fetchCounts.json";
  private static final String PUSH_COUNTS_FILE = "pushCounts.json";
  private static final String COUNT = "count";
  private static final String NAME = "name";

  private final File dataDir;
  private final Map<NameKey, AtomicLong> pushCounts;
  private final Map<NameKey, AtomicLong> fetchCounts;

  @Inject
  public CounterStore(
      @PluginData File dataDir,
      @Named(PUSH_COUNTS) ConcurrentMap<Project.NameKey, AtomicLong> pushCounts,
      @Named(FETCH_COUNTS) ConcurrentMap<Project.NameKey, AtomicLong> fetchCounts) {
    this.dataDir = dataDir;
    this.pushCounts = pushCounts;
    this.fetchCounts = fetchCounts;
  }

  @Override
  public void start() {
    readCountsAsJson(pushCounts, PUSH_COUNTS_FILE);
    readCountsAsJson(fetchCounts, FETCH_COUNTS_FILE);
  }

  @Override
  public void stop() {
    storeCounts(pushCounts, PUSH_COUNTS_FILE);
    storeCounts(fetchCounts, FETCH_COUNTS_FILE);
  }

  private void storeCounts(Map<NameKey, AtomicLong> counts, String fileName) {
    File output = new File(dataDir, fileName);
    if (output.exists()) {
      output.delete();
    }
    try (OutputStream os = new FileOutputStream(output)) {
      JsonArray data = toJson(counts);
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      String json = gson.toJson(data);
      os.write(json.getBytes(Charsets.UTF_8));
    } catch (IOException e) {
      log.error(
          "Can't serialize counts upon stop event. Last counts are lost.", e);
    }
  }

  private JsonArray toJson(Map<NameKey, AtomicLong> counts) {
    JsonArray array = new JsonArray();
    Set<Entry<NameKey, AtomicLong>> entries = counts.entrySet();
    for (Entry<NameKey, AtomicLong> entry : entries) {
      JsonObject object = new JsonObject();
      object.addProperty(NAME, entry.getKey().get());
      object.addProperty(COUNT, entry.getValue().get());
      array.add(object);
    }
    return array;
  }

  private void readCountsAsJson(Map<NameKey, AtomicLong> counts, String fileName) {
    File input = new File(dataDir, fileName);
    if (!input.exists()) {
      return;
    }
    try (InputStream is = new FileInputStream(input)) {
      Reader reader = new InputStreamReader(is, Charsets.UTF_8);
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(reader);
      JsonArray array = element.getAsJsonArray();
      for (JsonElement jsonElement : array) {
        JsonObject object = jsonElement.getAsJsonObject();
        String projectName = object.get(NAME).getAsString();
        long count = object.get(COUNT).getAsNumber().longValue();
        counts.put(new Project.NameKey(projectName), new AtomicLong(count));
      }
    } catch (IOException e) {
      log.error(
          "Can't deserialize counts upon start event. Persisted counts are lost.",
          e);
    }
  }
}
