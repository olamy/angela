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

public class JDK {

  private final String home;
  private final String version;
  private final String vendor;
  private final boolean valid;

  public JDK(String home, String version, String vendor, boolean valid) {
    this.home = home;
    this.version = version;
    this.vendor = vendor;
    this.valid = valid;
  }

  public String getHome() {
    return home;
  }

  public String getVersion() {
    return version;
  }

  public String getVendor() {
    return vendor;
  }

  public boolean isValid() {
    return valid;
  }

  @Override
  public String toString() {
    return "JDK{" +
        "home='" + home + '\'' +
        ", version='" + version + '\'' +
        ", vendor='" + vendor + '\'' +
        ", valid=" + valid +
        '}';
  }
}
