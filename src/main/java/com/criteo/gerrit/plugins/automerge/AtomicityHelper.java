package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.GetRelated;
import com.google.gerrit.server.change.GetRelated.RelatedInfo;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class AtomicityHelper {

  private final static Logger log = LoggerFactory.getLogger(AtomicityHelper.class);

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private ChangeControl.GenericFactory changeFactory;

  @Inject
  private ChangesCollection collection;

  @Inject
  AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  GetRelated getRelated;

  @Inject
  MergeUtil.Factory mergeUtilFactory;

  @Inject
  Provider<PostReview> reviewer;

  @Inject
  Submit submitter;

  @Inject
  Emails emails;

  /**
   * Check if the current patchset of the specified change has dependent
   * unmerged changes.
   *
   * @param project
   * @param number
   * @return true or false
   * @throws IOException
   * @throws NoSuchChangeException
   * @throws OrmException
   */
  public boolean hasDependentReview(String project, int number) throws IOException, NoSuchChangeException, OrmException {
      RevisionResource r = getRevisionResource(project, number);
    RelatedInfo related = getRelated.apply(r);
    log.debug(String.format("Checking for related changes on review %d", number));
    return related.changes.size() > 0;
  }

  /**
   * Check if a change is an atomic change or not. A change is atomic if it has
   * the atomic topic prefix.
   *
   * @param change a ChangeAttribute instance
   * @return true or false
   */
  public boolean isAtomicReview(final ChangeAttribute change) {
    final boolean atomic = change.topic != null && change.topic.startsWith(config.getTopicPrefix());
    log.debug(String.format("Checking if change %s is an atomic change: %b", change.number, atomic));
    return atomic;
  }

  /**
   * Check if a change is submitable.
   *
   * @param project a change project
   * @param change a change number
   * @return true or false
   * @throws OrmException
   */
  public boolean isSubmittable(String project, int change) throws OrmException {
    return changeDataFactory.create(db.get(), new Project.NameKey(project), new Change.Id(change)).isMergeable();
  }

  /**
   * Merge a review.
   *
   * @param info
   */
  public void mergeReview(ChangeInfo info) throws Exception {
    submitter.apply(getRevisionResource(info.project, info._number),
        new SubmitInput());
  }

  public RevisionResource getRevisionResource(String project, int changeNumber) throws NoSuchChangeException, OrmException {
    ChangeControl ctl = changeFactory.validateFor(db.get(), new Change.Id(changeNumber), getBotUser());
    ChangeData changeData = changeDataFactory.create(db.get(), new Project.NameKey(project), new Change.Id(changeNumber));
    RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    return r;
  }

  private IdentifiedUser getBotUser() {
    try {
      Set<Account.Id> ids = emails.getAccountFor(config.getBotEmail());
      if (ids.isEmpty()) {
        throw new RuntimeException("No user found with email: " + config.getBotEmail());
      }
      return factory.create(ids.iterator().next());
    } catch (IOException | OrmException e) {
      throw new RuntimeException("Unable to get account with email: " + config.getBotEmail(), e);
    }
  }
}
