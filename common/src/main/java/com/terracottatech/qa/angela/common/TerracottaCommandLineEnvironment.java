package com.terracottatech.qa.angela.common;

import java.util.List;
import java.util.Set;

public class TerracottaCommandLineEnvironment {

  private final String javaVersion;
  private final Set<String> javaVendors;
  private final List<String> javaOpts;

  public TerracottaCommandLineEnvironment(String javaVersion, Set<String> javaVendors, List<String> javaOpts) {
    this.javaVersion = javaVersion;
    this.javaVendors = javaVendors;
    this.javaOpts = javaOpts;
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public Set<String> getJavaVendors() {
    return javaVendors;
  }

  public List<String> getJavaOpts() {
    return javaOpts;
  }
}
