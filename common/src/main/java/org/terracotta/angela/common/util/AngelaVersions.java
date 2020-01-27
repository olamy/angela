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
