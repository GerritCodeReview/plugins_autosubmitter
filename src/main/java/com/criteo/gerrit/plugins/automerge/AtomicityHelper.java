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

import static com.google.gerrit.server.permissions.ChangePermission.READ;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.api.changes.RelatedChangeAndCommitInfo;
import com.google.gerrit.extensions.api.changes.RelatedChangesInfo;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.GetRelated;
import com.google.gerrit.server.restapi.change.Submit;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomicityHelper {

  private static final Logger log = LoggerFactory.getLogger(AtomicityHelper.class);

  @Inject ChangeData.Factory changeDataFactory;

  @Inject AutomergeConfig config;

  @Inject private IdentifiedUser.GenericFactory factory;

  @Inject GetRelated getRelated;

  @Inject Submit submitter;

  @Inject Emails emails;

  @Inject ChangeNotes.Factory changeNotesFactory;

  @Inject PermissionBackend permissionBackend;

  @Inject SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;

  @Inject ChangeResource.Factory changeResourceFactory;

  /**
   * Check if the current patchset of the specified change has dependent unmerged changes.
   *
   * @param project
   * @param number
   * @return true or false
   * @throws IOException
   * @throws NoSuchChangeException
   * @throws NoSuchProjectException
   * @throws OrmException
   * @throws PermissionBackendException
   */
  public boolean hasDependentReview(String project, int number)
      throws RestApiException, IOException, NoSuchChangeException, NoSuchProjectException,
          OrmException, PermissionBackendException {
    RevisionResource r = getRevisionResource(project, number);
    RelatedChangesInfo related = getRelated.apply(r);
    log.debug(String.format("Checking for related changes on review %d", number));

    String checkedCommitSha1 = r.getPatchSet().getRevision().get();
    int firstParentIndex = 0;
    int i = 0;
    for (RelatedChangeAndCommitInfo c : related.changes) {
      if (checkedCommitSha1.equals(c.commit.commit)) {
        firstParentIndex = i + 1;
        log.debug(
            String.format(
                "First parent index on review %d is %d on commit %s",
                number, firstParentIndex, c.commit.commit));
        break;
      }
      i++;
    }

    boolean hasNonMergedParent = false;
    for (RelatedChangeAndCommitInfo c :
        related.changes.subList(firstParentIndex, related.changes.size())) {
      if (!ChangeStatus.MERGED.toString().equals(c.status)) {
        log.info(
            String.format(
                "Found non merged parent commit on review %d: %s", number, c.commit.commit));
        hasNonMergedParent = true;
        break;
      }
    }

    return hasNonMergedParent;
  }

  /**
   * Check if a change is an atomic change or not. A change is atomic if it has the atomic topic
   * prefix.
   *
   * @param change a Change instance
   * @return true or false
   */
  public boolean isAtomicReview(final Change change) {
    final boolean atomic = change.topic != null && change.topic.startsWith(config.getTopicPrefix());
    log.debug(
        String.format("Checking if change %s is an atomic change: %b", change.number, atomic));
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
    ChangeData changeData =
        changeDataFactory.create(
            new Project.NameKey(project), new com.google.gerrit.reviewdb.client.Change.Id(change));
    // For draft reviews, the patchSet must be set to avoid an NPE.
    final List<SubmitRecord> cansubmit =
        submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults()).evaluate(changeData);
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

  /** Merge a review. */
  public void mergeReview(String project, int number) throws Exception {
    submitter.apply(getRevisionResource(project, number), new SubmitInput());
  }

  public RevisionResource getRevisionResource(String project, int changeNumber)
      throws RestApiException, OrmException, IOException {
    com.google.gerrit.reviewdb.client.Change.Id changeId =
        new com.google.gerrit.reviewdb.client.Change.Id(changeNumber);
    ChangeNotes notes = changeNotesFactory.createChecked(changeId);
    try {
      permissionBackend.user(getBotUser()).change(notes).check(READ);
      ChangeData changeData = changeDataFactory.create(new Project.NameKey(project), changeId);

      RevisionResource r =
          new RevisionResource(
              changeResourceFactory.create(changeData.notes(), getBotUser()),
              changeData.currentPatchSet());
      return r;
    } catch (AuthException | PermissionBackendException e) {
      throw new NoSuchChangeException(changeId);
    }
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
