// Copyright (C) 2019 The Android Open Source Project
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

package com.criteo.gerrit.plugins.automerge;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class AutomergeConfigTest {

  @Test
  public void testGetDefaultConfig() throws IOException {
    final Config conf = new Config();
    final SitePaths paths = new SitePaths(Paths.get("."));

    final AutomergeConfig amconf = new AutomergeConfig(conf, paths);

    assertEquals(amconf.getBotEmail(), AutomergeConfig.getDefaultBotEmail());
    assertEquals(amconf.getTopicPrefix(), AutomergeConfig.getDefaultTopicPrefix());
  }

  @Test
  public void testGetValues() throws IOException {
    final Config conf = new Config();
    final SitePaths paths = new SitePaths(Paths.get("."));

    conf.setString(
        AutomergeConfig.AUTOMERGE_SECTION, null, AutomergeConfig.BOT_EMAIL_KEY, "Foo@bar.com");
    conf.setString(
        AutomergeConfig.AUTOMERGE_SECTION,
        null,
        AutomergeConfig.TOPIC_PREFIX_KEY,
        "fake_topic_prefix");

    final AutomergeConfig amconf = new AutomergeConfig(conf, paths);
    assertEquals(amconf.getBotEmail(), "Foo@bar.com");
    assertEquals(amconf.getTopicPrefix(), "fake_topic_prefix");
  }
}
