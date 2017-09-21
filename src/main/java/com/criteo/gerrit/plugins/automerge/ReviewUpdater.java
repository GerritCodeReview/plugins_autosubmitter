package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewUpdater {
  private static final Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  /**
   * Prefix used in front of messages pushed to Gerrit by this plugin. This prefix is used to
   * discriminate the messages emitted by the plugin from the other messages and avoid infinite
   * loops.
   */
  public static final String commentsPrefix = "[Autosubmitter] ";

  @Inject Provider<PostReview> reviewer;

  @Inject private AtomicityHelper atomicityHelper;

  public void commentOnReview(String project, int number, String comment) throws Exception {
    ReviewInput reviewInput = createComment(comment);
    applyComment(project, number, reviewInput);
  }

  private ReviewInput createComment(String comment) {
    return new ReviewInput().message(commentsPrefix + comment);
  }

  private void applyComment(String project, int number, ReviewInput comment) throws Exception {
    RevisionResource r = atomicityHelper.getRevisionResource(project, number);
    reviewer.get().apply(r, comment);
  }
}
