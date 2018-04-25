package com.criteo.gerrit.plugins.automerge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A comment pushed by the plugin to a Gerrit patchset. */
public class PluginComment {

  private static final Logger log = LoggerFactory.getLogger(PluginComment.class);

  private final File templatePath;
  private final String defaultMessage;

  PluginComment(File templatePath, String defaultMessage) {
    this.templatePath = templatePath;
    this.defaultMessage = defaultMessage;
  }

  /**
   * Returns the comment message, possibly with interpolation placeholders.
   *
   * @return a string
   */
  String getContent() {
    if (templatePath.exists()) {
      try {
        return Files.asCharSource(templatePath,  Charsets.UTF_8).read();
      } catch (final IOException exc) {
        log.error("Not able to read " + templatePath, exc);
      }
    }
    return defaultMessage;
  }
}
