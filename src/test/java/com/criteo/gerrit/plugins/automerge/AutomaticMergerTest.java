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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@TestPlugin(
    name = "autosubmitter",
    sysModule = "com.criteo.gerrit.plugins.automerge.AutomergeModule")
public class AutomaticMergerTest extends LightweightPluginDaemonTest {
  private static final String BOT_USERS = "Bot Users";
  private static final String DEVELOPERS = "Developers";
  private TestAccount botUser;
  private TestAccount regularUser;
  @Inject ExternalIds extIds;
  @Inject RequestScopeOperations requestScopeOperations;

  @Before
  public void setup() throws Exception {
    gApi.groups().create(BOT_USERS);
    gApi.groups().create(DEVELOPERS);
    botUser = accountCreator.create("botuser", "botuser@mycompany.com", "Bot User", BOT_USERS);
    regularUser =
        accountCreator.create("developer", "developer@mycompany.com", "Developer", DEVELOPERS);
    grant(project, "refs/*", "submit", false, groupUUID(BOT_USERS));
    grantLabel("Code-Review", -2, 2, project, "refs/*", false, groupUUID(DEVELOPERS), false);
  }

  @Test
  @GerritConfig(name = "automerge.botEmail", value = "botuser@mycompany.com")
  public void changeReviewedShouldNotBeAutomaticallyMergedIfNotApproved() throws Exception {
    int changeNum = createChangeNum(user);

    assertThat(changesApi().id(changeNum).get().status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  @GerritConfig(name = "automerge.botEmail", value = "botuser@mycompany.com")
  public void changeReviewedShouldBeAutomaticallyMergedOnceApproved() throws Exception {
    int changeNum = createChangeNum(user);
    changesApi().id(changeNum).current().review(ReviewInput.approve());

    assertThat(changesApi().id(changeNum).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @GerritConfig(name = "automerge.botEmail", value = "botuser@mycompany.com")
  public void changeShouldBeAutomaticallyMergedByBotUser() throws Exception {
    int changeNum = createChangeNum(user);
    ChangeApi changeApi = changesApi().id(changeNum);
    changeApi.current().review(ReviewInput.approve());

    ChangeInfo changeInfo = changeApi.get();
    assertThat(changeInfo.submitter).isNotNull();
    assertThat(changeInfo.submitter._accountId).isEqualTo(new Integer(botUser.id.get()));
    assertThat(changeInfo.submitter.email).isEqualTo(botUser.email);
  }

  private AccountGroup.UUID groupUUID(String name) {
    return groupCache.get(new AccountGroup.NameKey(name)).get().getGroupUUID();
  }

  private Changes changesApi() {
    requestScopeOperations.setApiUser(regularUser.id);
    return gApi.changes();
  }

  private int createChangeNum(TestAccount user) throws Exception {
    List<String> msgs =
        Arrays.asList(createChangeAsUser("refs/for/master", user).getMessage().split("\n"));
    String changeUrl = msgs.get(msgs.size() - 1).trim().split(" ")[0];
    return Integer.parseInt(changeUrl.substring(changeUrl.lastIndexOf('/') + 1));
  }

  protected PushOneCommit.Result createChangeAsUser(String ref, TestAccount user) throws Exception {
    PushOneCommit.Result result = pushFactory.create(user.getIdent(), testRepo).to(ref);
    result.assertOkStatus();
    return result;
  }
}
