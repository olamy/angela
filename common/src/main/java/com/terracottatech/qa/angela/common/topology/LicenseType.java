package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.tcconfig.License;

import java.net.URL;

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
    final URL licenseResource = LicenseType.class.getResource(defaultLicenseResourceName);
    if (licenseResource == null) {
      return null;
    } else {
      return new License(licenseResource);
    }
  }
}
