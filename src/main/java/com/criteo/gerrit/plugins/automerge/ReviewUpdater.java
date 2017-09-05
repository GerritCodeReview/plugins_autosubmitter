package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ReviewUpdater {
  private final static Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  /**
   * Prefix used in front of messages pushed to Gerrit by this plugin. This prefix is used to
   * discriminate the messages emitted by the plugin from the other messages and avoid infinite
   * loops.
   */
  public static final String commentsPrefix = "[Autosubmitter] ";

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

  @Inject
  Provider<PostReview> reviewer;

  @Inject
  private AtomicityHelper atomicityHelper;

  public void commentOnReview(String project, int number, PluginComment pluginComment) throws RestApiException, OrmException, IOException, NoSuchChangeException, UpdateException {
    ReviewInput comment = createComment(pluginComment);
    applyComment(project, number, comment);
  }

  public void setMinusOne(String project, int number, PluginComment pluginComment) throws RestApiException, OrmException, IOException, NoSuchChangeException, UpdateException {
    ReviewInput message = createComment(pluginComment).label("Code-Review", -1);
    applyComment(project, number, message);
  }

  private ReviewInput createComment(PluginComment pluginComment) {
    return new ReviewInput().message(commentsPrefix + pluginComment.getCommentContent());
  }

  private void applyComment(String project, int number, ReviewInput comment) throws RestApiException, OrmException, IOException, NoSuchChangeException, UpdateException {
    RevisionResource r = atomicityHelper.getRevisionResource(project, number);
    reviewer.get().apply(r, comment);
  }
}
