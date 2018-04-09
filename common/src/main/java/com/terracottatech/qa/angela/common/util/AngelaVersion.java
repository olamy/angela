package com.terracottatech.qa.angela.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

public class AngelaVersion {

  private static final String ANGELA_VERSION;

  static {
    try {
      Properties versionsProps = new Properties();
      try (InputStream in = AngelaVersion.class.getResourceAsStream("/angela/angela-version.properties")) {
        versionsProps.load(in);
      }
      ANGELA_VERSION = versionsProps.getProperty("angela.version");
    } catch (IOException ioe) {
      throw new RuntimeException("Cannot find /angela/angela-version.properties in classpath");
    }
  }

  public static String getAngelaVersion() {
    return ANGELA_VERSION;
  }
}
