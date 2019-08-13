package com.terracottatech.qa.angela.common.topology;

/**
 * @author Aurelien Broszniowski
 */
public enum LicenseType {

  // 5.x:
  OS(true, null),
  TERRACOTTA(false, "terracotta-db"),

  // 4.x:
  GO(false, "bigmemory-go"),
  MAX(false, "bigmemory-max");

  private final boolean opensource;
  private final String kratosTag;

  LicenseType(boolean opensource, String kratosTag) {
    this.opensource = opensource;
    this.kratosTag = kratosTag;
  }

  public boolean isOpenSource() {
    return opensource;
  }

  public String getKratosTag() {
    return kratosTag;
  }
}
