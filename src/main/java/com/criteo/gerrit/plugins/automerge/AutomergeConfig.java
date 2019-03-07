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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.File;
import org.eclipse.jgit.lib.Config;

public class AutomergeConfig {
  public static final String AUTOMERGE_SECTION = "automerge";
  public static final String BOT_EMAIL_KEY = "botEmail";

  public final PluginComment atomicReviewDetected;
  public final PluginComment atomicReviewsSameRepo;
  public final PluginComment cantMergeGitConflict;
  public final PluginComment cantMergeDependsOnNonMerged;

  private static final String defaultBotEmail = "qabot@criteo.com";
  private static final String defaultTopicPrefix = "crossrepo/";
  public static final String TOPIC_PREFIX_KEY = "topicPrefix";

  public static final String getDefaultBotEmail() {
    return defaultBotEmail;
  }

  public static final String getDefaultTopicPrefix() {
    return defaultTopicPrefix;
  }

  private String botEmail;
  private final File templatesPath;
  private String topicPrefix;

  @Inject
  public AutomergeConfig(@GerritServerConfig final Config config, final SitePaths paths) {
    botEmail = config.getString(AUTOMERGE_SECTION, null, BOT_EMAIL_KEY);
    if (botEmail == null) {
      botEmail = defaultBotEmail;
    }
    topicPrefix = config.getString(AUTOMERGE_SECTION, null, TOPIC_PREFIX_KEY);
    if (topicPrefix == null) {
      topicPrefix = defaultTopicPrefix;
    }

    templatesPath = paths.etc_dir.toFile();

    atomicReviewDetected =
        new PluginComment(
            getCommentPath("atomic_review_detected.txt"),
            "This review is part of a cross-repository change.\n"
                + "It will be submitted once all related reviews are submittable.");
    atomicReviewsSameRepo =
        new PluginComment(
            getCommentPath("atomic_review_same_repo.txt"),
            "This cross-repo review depends on a not merged commit that must be merged first.");
    cantMergeGitConflict =
        new PluginComment(
            getCommentPath("cantmerge_git_conflict.txt"),
            "This cross-repo review is blocked by a git conflict on change #/c/%d.");
    cantMergeDependsOnNonMerged =
        new PluginComment(
            getCommentPath("cantmerge_depends_on_non_merged.txt"),
            "This cross-repo review is blocked by a non merged commit below #/c/%d.");
  }

  public final String getBotEmail() {
    return botEmail;
  }

  public final File getCommentPath(String fileName) {
    return new File(templatesPath.getPath(), fileName);
  }

  public final String getTopicPrefix() {
    return topicPrefix;
  }
}
