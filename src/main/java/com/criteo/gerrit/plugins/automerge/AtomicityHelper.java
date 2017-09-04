package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.GetRelated;
import com.google.gerrit.server.change.GetRelated.ChangeAndCommit;
import com.google.gerrit.server.change.GetRelated.RelatedInfo;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class AtomicityHelper {

  private final static Logger log = LoggerFactory.getLogger(AtomicityHelper.class);

  @Inject
  private AccountByEmailCache byEmailCache;

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

    String checkedCommitSha1 = r.getPatchSet().getRevision().get();
    int firstParentIndex = 0;
    int i = 0;
    for (ChangeAndCommit c: related.changes) {
      if (checkedCommitSha1.equals(c.commit.commit)) {
          firstParentIndex = i+1;
          log.debug(String.format("First parent index on review %d is %d on commit %s", number, firstParentIndex, c.commit.commit));
          break;
      }
      i++;
    }

    boolean hasNonMergedParent = false;
    for (ChangeAndCommit c: related.changes.subList(firstParentIndex, related.changes.size())) {
      if (!ChangeStatus.MERGED.toString().equals(c.status)) {
         log.info(String.format("Found non merged parent commit on review %d: %s", number, c.commit.commit));
         hasNonMergedParent = true;
         break;
      }
    }

    return hasNonMergedParent;
  }

  /**
   * Check if a change is an atomic change or not. A change is atomic if it has
   * the atomic topic prefix.
   *
   * @param change a ChangeAttribute instance
   * @return true or false
   */
  public boolean isAtomicReview(final Change change) {
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
    ChangeData changeData = changeDataFactory.create(db.get(), new Project.NameKey(project), new com.google.gerrit.reviewdb.client.Change.Id(change));
    // For draft reviews, the patchSet must be set to avoid an NPE.
    final List<SubmitRecord> cansubmit = new SubmitRuleEvaluator(changeData).setPatchSet(changeData.currentPatchSet()).evaluate();
    log.debug(String.format("Checking if change %d is submitable.", change));
    for (SubmitRecord submit : cansubmit) {
      if (submit.status != SubmitRecord.Status.OK) {
        log.debug(String.format("Change %d is not submitable", change));
        return false;
      }
    }
    log.debug(String.format("Change %d is submitable", change));
    return true;
  }

  /**
   * Merge a review.
   *
   * @param info
   * @throws RestApiException
   * @throws NoSuchChangeException
   * @throws OrmException
   * @throws IOException
   */
  public void mergeReview(String project, int changeNumber)
      throws RestApiException, NoSuchChangeException, OrmException, IOException {
    final SubmitInput input = new SubmitInput();
    input.waitForMerge = true;
    final RevisionResource r = getRevisionResource(project, changeNumber);
    submitter.apply(r, input);
  }

  public RevisionResource getRevisionResource(String project, int changeNumber) throws NoSuchChangeException, OrmException {
    ChangeControl ctl = changeFactory.validateFor(db.get(), new com.google.gerrit.reviewdb.client.Change.Id(changeNumber), getBotUser());
    ChangeData changeData = changeDataFactory.create(db.get(), new Project.NameKey(project), new com.google.gerrit.reviewdb.client.Change.Id(changeNumber));
    RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    return r;
  }

  private IdentifiedUser getBotUser() {
    final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
    if (ids.isEmpty()) {
      throw new RuntimeException("No user found with email: " + config.getBotEmail());
    }
    final IdentifiedUser bot = factory.create(ids.iterator().next());
    return bot;
  }
}
