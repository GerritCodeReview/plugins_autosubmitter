package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.data.ChangeAttribute;

/**
 * A change represented only with the fields that this plugin requires.
 *
 * <p>As a change can be a {@link ChangeAttribute} or a {@link ChangeInfo}, this intermediate class
 * is needed.
 */
public class Change {
  public final String project;
  public final int number;
  public final String id;
  public final String topic;
  public final String branch;

  private Change(String project, int number, String id, String topic, String branch) {
    this.project = project;
    this.number = number;
    this.id = id;
    this.topic = topic;
    this.branch = branch;
  }

  public static Change from(ChangeAttribute changeAttribute) {
    return new Change(
        changeAttribute.project,
        changeAttribute.number,
        changeAttribute.id,
        changeAttribute.topic,
        changeAttribute.branch);
  }

  public static Change from(ChangeInfo changeInfo) {
    return new Change(
        changeInfo.project, changeInfo._number, changeInfo.id, changeInfo.topic, changeInfo.branch);
  }
}
