// Copyright 2014 Criteo
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

import com.google.common.collect.Lists;
import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.GetRelated;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.events.TopicChangedEvent;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

/**
 * Starts at the same time as the gerrit server, and sets up our change hook
 * listener.
 */
public class AutomaticMerger implements EventListener, LifecycleListener {

  private final static Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  @Inject
  private GerritApi api;

  @Inject
  private AtomicityHelper atomicityHelper;

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

  @Inject
  GetRelated getRelated;

  @Inject
  MergeUtil.Factory mergeUtilFactory;

  @Inject
  Provider<PostReview> reviewer;

  @Inject
  private ReviewUpdater reviewUpdater;

  @Inject
  Submit submitter;

  @Override
  synchronized public void onEvent(final Event event) {
    if (event instanceof TopicChangedEvent) {
      onTopicChanged((TopicChangedEvent)event);
    }
    else if (event instanceof PatchSetCreatedEvent) {
      onPatchSetCreated((PatchSetCreatedEvent)event);
    }
    else if (event instanceof CommentAddedEvent) {
      onCommentAdded((CommentAddedEvent)event);
    }
    // it is not an else since the previous automatic submit(s) can potentially
    // trigger others on the whole project/branch
    if (event instanceof RefUpdatedEvent) {
      onRefUpdatedEvent((RefUpdatedEvent) event);
    }
  }


  private void onTopicChanged(final TopicChangedEvent event) {
    Change change = Change.from(event.change.get());
    if (!atomicityHelper.isAtomicReview(change)) {
      return;
    }
    processNewAtomicPatchSet(change);
  }

  private void onPatchSetCreated(final PatchSetCreatedEvent event) {
    Change change = Change.from(event.change.get());
    if (atomicityHelper.isAtomicReview(change)) {
      processNewAtomicPatchSet(change);
    }

    try {
      autoSubmitIfMergeable(change);
    } catch (OrmException | RestApiException | IOException | UpdateException e) {
      log.error("An exception occured while trying to merge change #" + change.number, e);
    }
  }

  private void onCommentAdded(final CommentAddedEvent newComment) {
    if (!shouldProcessCommentEvent(newComment)) {
      return;
    }

    ChangeAttribute change = newComment.change.get();
    try {
      checkReviewExists(change.number);
      autoSubmitIfMergeable(Change.from(change));
    } catch (RestApiException | OrmException | UpdateException | IOException e) {
      log.error("An exception occured while trying to atomic merge a change.", e);
      throw new RuntimeException(e);
    }
  }

  private void onRefUpdatedEvent(final RefUpdatedEvent event) {
    String refName = event.getRefName();
    String projectName = event.getProjectNameKey().get();
    try {
      api.changes()
          .query("branch:" + refName + " project:" + projectName + " is:submittable")
          .get()
          .forEach(
              submittable -> {
                try {
                  log.info(
                      "Found another submittable change #"
                          + submittable._number
                          + " on project "
                          + projectName
                          + " during update of ref "
                          + refName
                          + ": Submitting ...");
                  autoSubmitIfMergeable(Change.from(submittable));
                } catch (UpdateException | RestApiException | OrmException | IOException e) {
                  log.error(
                      "Cannot autosubmit change "
                          + submittable._number
                          + " on project "
                          + projectName
                          + " to ref "
                          + refName,
                      e);
                }
              });
    } catch (RestApiException e) {
      log.error(
          "Cannot query submittable changes on project " + projectName + " for ref " + refName);
    }
  }

  private void autoSubmitIfMergeable(Change change)
      throws OrmException, RestApiException, NoSuchChangeException, IOException, UpdateException {
    if (atomicityHelper.isSubmittable(change.project, change.number)) {
      if (atomicityHelper.isAtomicReview(change)) {
        attemptToMergeAtomic(change);
      } else {
        log.info("Submitting non-atomic change {}...", change.number);
        atomicityHelper.mergeReview(change.project, change.number);
      }
    }
  }

  /**
   * Returns true if the plugin must handle this comment, i.e. if we are sure it does not come
   * from this plugin (to avoid infinite loop).
   *
   * @param comment
   * @return a boolean
   */
  private boolean shouldProcessCommentEvent(CommentAddedEvent comment) {
    AccountAttribute account = comment.author.get();
    if (!config.getBotEmail().equals(account.email)) {
      return true;
    }
    ApprovalAttribute[] approvals = comment.approvals.get();
    if (approvals != null) {
      for (ApprovalAttribute approval : approvals) {
        // See ReviewUpdate#setMinusOne
        if (!("Code-Review".equals(approval.type) && "-1".equals(approval.value))) {
          return true;
        }
      }
    }
    return false;
  }

  private void attemptToMergeAtomic(Change change)
      throws RestApiException, OrmException, NoSuchChangeException, IOException, UpdateException {
    final List<ChangeInfo> related = Lists.newArrayList();
    related.addAll(
        api.changes()
            .query("status: open AND topic: " + change.topic)
            .withOption(ListChangesOption.CURRENT_REVISION)
            .get());
    for (final ChangeInfo info : related) {
      if (!atomicityHelper.isSubmittable(info.project, info._number)) {
        log.info("Change {} is not submittable because same topic change {} has not all approvals.", change.number, info._number);
        return;
      }
    }

    for (final ChangeInfo info : related) {
      boolean dependsOnNonMergedCommit = atomicityHelper.hasDependentReview(info.project, info._number);
      if (!info.mergeable || dependsOnNonMergedCommit) {
        log.info("Change {} is not mergeable because same topic change {} {}", change.number, info._number,
            !info.mergeable ? "is non mergeable" : "depends on a non merged commit.");
        if (!info.mergeable) {
          reviewUpdater.commentOnReview(change.project, change.number, AutomergeConfig.CANT_MERGE_COMMENT_FILE);
        }
        return;
      }
    }

    log.info("Submitting atomic change {}...", change.number);
    for (final ChangeInfo info : related) {
      atomicityHelper.mergeReview(info.project, info._number);
    }
  }

  private void processNewAtomicPatchSet(Change change) {
    try {
      checkReviewExists(change.number);
      if (atomicityHelper.hasDependentReview(change.project, change.number)) {
        log.info(String.format("Warn the user by setting -1 on change %d, as other atomic changes exists on the same repository.",
            change.number));
        reviewUpdater.setMinusOne(change.project, change.number, AutomergeConfig.ATOMIC_REVIEWS_SAME_REPO_FILE);
      } else {
        log.info(String.format("Detected atomic review on change %d.", change.number));
        reviewUpdater.commentOnReview(change.project, change.number, AutomergeConfig.ATOMIC_REVIEW_DETECTED_FILE);
      }
    } catch (RestApiException | IOException | OrmException | UpdateException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkReviewExists(int reviewNumber) throws RestApiException {
    api.changes().id(reviewNumber).get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
  }

  @Override
  public void start() {
    log.info("Starting automatic merger plugin.");
  }

  @Override
  public void stop() {
  }
}
