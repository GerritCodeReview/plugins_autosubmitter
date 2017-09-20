package com.criteo.gerrit.plugins.automerge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewUpdater {
  private static final Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  @Inject ChangeData.Factory changeDataFactory;

  @Inject AutomergeConfig config;

  @Inject Provider<ReviewDb> db;

  @Inject Provider<PostReview> reviewer;

  @Inject private AtomicityHelper atomicityHelper;

  public void commentOnReview(String project, int number, String commentTemplate) throws Exception {
    ReviewInput comment = createComment(commentTemplate);
    applyComment(project, number, comment);
  }

  public void setMinusOne(String project, int number, String commentTemplate) throws Exception {
    ReviewInput message = createComment(commentTemplate).label("Code-Review", -1);
    applyComment(project, number, message);
  }

  private ReviewInput createComment(final String commentTemplate) {
    return new ReviewInput().message(getCommentFromFile(commentTemplate));
  }

  private String getCommentFromFile(final String filename) {
    try {
      return Files.asCharSource(new File(config.getTemplatesPath(), filename), Charsets.UTF_8)
          .read();
    } catch (final IOException exc) {
      final String errmsg =
          String.format(
              "Cannot find %s file in gerrit etc dir. Please check your gerrit configuration",
              filename);
      log.error(errmsg);
      return errmsg;
    }
  }

  private void applyComment(String project, int number, ReviewInput comment) throws Exception {
    RevisionResource r = atomicityHelper.getRevisionResource(project, number);
    reviewer.get().apply(r, comment);
  }
}
