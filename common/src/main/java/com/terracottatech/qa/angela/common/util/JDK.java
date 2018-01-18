package com.terracottatech.qa.angela.common.util;

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
