package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.tcconfig.License;

/**
 * @author Aurelien Broszniowski
 */
public enum LicenseType {
  // 3.x
  EHCACHE_OS(null, null),

  // 4.x:
  GO("bigmemory-go", new License(LicenseType.class.getResource("/licenses/terracotta/4/terracotta-license.key"))),
  MAX("bigmemory-max", new License(LicenseType.class.getResource("/licenses/terracotta/4/terracotta-license.key"))),

  // 10.x:
  TERRACOTTA("terracotta-db", new License(LicenseType.class.getResource("/licenses/terracotta/10/Terracotta101.xml"))),
  ;

  private final String kratosTag;
  private final License defaultLicense;

  LicenseType(String kratosTag, License defaultLicense) {
    this.kratosTag = kratosTag;
    this.defaultLicense = defaultLicense;
  }

  public boolean isOpenSource() {
    return kratosTag == null;
  }

  public String getKratosTag() {
    return kratosTag;
  }

  public License defaultLicense() {
    return defaultLicense;
  }
}
