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

package org.terracotta.angela.common.topology;

import org.terracotta.angela.common.tcconfig.License;

/**
 * @author Aurelien Broszniowski
 */
public enum LicenseType {
  // 3.x
  EHCACHE_OS(null, null),

  // 4.x:
  GO("bigmemory-go", "/licenses/terracotta-license.key"),

  MAX("bigmemory-max", "/licenses/terracotta-license.key"),

  // 10.x:
  TERRACOTTA("terracotta-db", "/licenses/Terracotta101.xml"),
  ;

  private final String kratosTag;
  private final String defaultLicenseResourceName;

  LicenseType(String kratosTag, String defaultLicenseResourceName) {
    this.kratosTag = kratosTag;
    this.defaultLicenseResourceName = defaultLicenseResourceName;
  }

  public boolean isOpenSource() {
    return kratosTag == null;
  }

  public String getKratosTag() {
    return kratosTag;
  }

  public License defaultLicense() {
    return defaultLicenseResourceName == null ? null : new License(LicenseType.class.getResource(defaultLicenseResourceName));
  }
}
