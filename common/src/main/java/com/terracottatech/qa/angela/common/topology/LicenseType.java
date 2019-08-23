package com.terracottatech.qa.angela.common.topology;

/**
 * @author Aurelien Broszniowski
 */
public enum LicenseType {
  // 3.x
  EHCACHE_OS(null),

  // 10.x:
  TERRACOTTA("terracotta-db"),

  // 4.x:
  GO("bigmemory-go"),
  MAX("bigmemory-max");

  private final String kratosTag;

  LicenseType(String kratosTag) {
    this.kratosTag = kratosTag;
  }

  public boolean isOpenSource() {
    return kratosTag == null;
  }

  public String getKratosTag() {
    return kratosTag;
  }
}
