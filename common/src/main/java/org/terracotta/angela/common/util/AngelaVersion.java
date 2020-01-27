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

package org.terracotta.angela.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
