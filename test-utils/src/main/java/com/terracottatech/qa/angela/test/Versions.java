package com.terracottatech.qa.angela.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Versions {

  public static final String TERRACOTTA_VERSION;
  public static final String EHCACHE_OS_VERSION;
  public static final String TERRACOTTA_VERSION_4X;

  static {
    try {
      Properties versionsProps = new Properties();
      try (InputStream in = Versions.class.getResourceAsStream("versions.properties")) {
        versionsProps.load(in);
      }
      TERRACOTTA_VERSION = versionsProps.getProperty("terracotta.version");
      EHCACHE_OS_VERSION = versionsProps.getProperty("ehcache-os.version");
      TERRACOTTA_VERSION_4X = versionsProps.getProperty("terracotta-4x.version");
    } catch (IOException ioe) {
      throw new RuntimeException("Cannot find versions.properties in classpath");
    }
  }

}
