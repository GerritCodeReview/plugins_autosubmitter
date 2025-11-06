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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.RelatedChangeAndCommitInfo;
import com.google.gerrit.extensions.api.changes.RelatedChangesInfo;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.GetRelated;
import com.google.gerrit.server.restapi.change.Submit;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
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
   * @throws PermissionBackendException
   */
  public boolean hasDependentReview(String project, int number) throws Exception {
    RevisionResource r = getRevisionResource(project, number);
    RelatedChangesInfo related = getRelated.apply(r).value();
    log.debug(String.format("Checking for related changes on review %d", number));

    String checkedCommitSha1 = r.getPatchSet().commitId().name();
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
   */
  public boolean isSubmittable(String project, int change) {
    ChangeData changeData =
        changeDataFactory.create(
            Project.nameKey(project), com.google.gerrit.entities.Change.id(change));

    for (Map.Entry<SubmitRequirement, SubmitRequirementResult> req :
        changeData.submitRequirementsIncludingLegacy().entrySet()) {
      log.debug(String.format("Checking if change %d is submitable.", change));
      if (!req.getValue().fulfilled()) {
        log.info(
            String.format(
                "Change %d is not submitable: requirement %s is not fulfilled",
                change, req.getKey().name()));
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

  public RevisionResource getRevisionResource(String project, int changeNumber) {
    com.google.gerrit.entities.Change.Id changeId =
        com.google.gerrit.entities.Change.id(changeNumber);
    ChangeNotes notes = changeNotesFactory.createChecked(Project.nameKey(project), changeId);
    try {
      permissionBackend.user(getBotUser()).change(notes).check(READ);
      ChangeData changeData = changeDataFactory.create(Project.nameKey(project), changeId);

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
    } catch (IOException | StorageException e) {
      throw new RuntimeException("Unable to get account with email: " + config.getBotEmail(), e);
    }
  }
}
