package com.terracottatech.qa.angela.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AngelaVersions {

  public static final AngelaVersions INSTANCE = new AngelaVersions();

  private final Properties properties;

  private AngelaVersions() {
    try {
      try (InputStream in = getClass().getResourceAsStream("/angela/versions.properties")) {
        properties = new Properties();
        properties.load(in);
      }
    } catch (IOException ioe) {
      throw new RuntimeException("Error loading resource file /angela/versions.properties", ioe);
    }
  }

  public String getAngelaVersion() {
    return properties.getProperty("angela.version");
  }

  public boolean isSnapshot() {
    return getAngelaVersion().endsWith("-SNAPSHOT");
  }

}
