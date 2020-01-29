/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Versions {

  public static final String TERRACOTTA_VERSION;
  public static final String EHCACHE_OS_VERSION;
  public static final String EHCACHE_OS_SNAPSHOT_VERSION;
  public static final String TERRACOTTA_VERSION_4X;

  static {
    try {
      Properties versionsProps = new Properties();
      try (InputStream in = Versions.class.getResourceAsStream("versions.properties")) {
        versionsProps.load(in);
      }
      TERRACOTTA_VERSION = versionsProps.getProperty("terracotta.version");
      EHCACHE_OS_VERSION = versionsProps.getProperty("ehcache-os.version");
      EHCACHE_OS_SNAPSHOT_VERSION = versionsProps.getProperty("ehcache-os-snapshot.version");
      TERRACOTTA_VERSION_4X = versionsProps.getProperty("terracotta-4x.version");
    } catch (IOException ioe) {
      throw new RuntimeException("Cannot find versions.properties in classpath");
    }
  }

}
