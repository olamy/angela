package com.terracottatech.qa.angela.common.util;

public class JDK {

  private final String home;
  private final String version;
  private final String vendor;

  public JDK(String home, String version, String vendor) {
    this.home = home;
    this.version = version;
    this.vendor = vendor;
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

  @Override
  public String toString() {
    return "JDK{" +
        "home='" + home + '\'' +
        ", version='" + version + '\'' +
        ", vendor='" + vendor + '\'' +
        '}';
  }
}
