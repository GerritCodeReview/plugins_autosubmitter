package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AutomergeConfig {
  public final static String AUTOMERGE_SECTION = "automerge";
  public final static String BOT_EMAIL_KEY = "botEmail";

  public final PluginComment atomicReviewDetected;
  public final PluginComment atomicReviewsSameRepo;
  public final PluginComment cantMergeGitConflict;
  public final PluginComment cantMergeDependsOnNonMerged;

  private final static String defaultBotEmail = "qabot@criteo.com";
  private final static String defaultTopicPrefix = "crossrepo/";
  public final static String TOPIC_PREFIX_KEY = "topicPrefix";

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

    atomicReviewDetected = new PluginComment(getCommentPath("atomic_review_detected.txt"),
        "This review is part of a cross-repository change.\n"
            + "It will be submitted once all related reviews are submittable.");
    atomicReviewsSameRepo = new PluginComment(getCommentPath("atomic_review_same_repo.txt"),
        "This review depends on an unmerged commit.\n"
            + "This is not supported for cross-repository reviews.");
    cantMergeGitConflict = new PluginComment(getCommentPath("cantmerge.txt"),
        "This review has all approvals but is not submittable due to a git conflict.");
    cantMergeDependsOnNonMerged = new PluginComment(getCommentPath("cantmerge_depends_on_non_merged.txt.txt"),
        "This review has all approvals but is not submittable because it depends on a non merged commit.");
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
