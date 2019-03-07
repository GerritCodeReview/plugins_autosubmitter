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

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class ReviewUpdater {
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
