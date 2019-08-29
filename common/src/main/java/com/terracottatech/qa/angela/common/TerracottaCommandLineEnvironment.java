package com.terracottatech.qa.angela.common;

import java.util.Collections;
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

  public TerracottaCommandLineEnvironment withJavaVersion(String javaVersion) {
    return new TerracottaCommandLineEnvironment(javaVersion, javaVendors, javaOpts);
  }

  public TerracottaCommandLineEnvironment withJavaVendors(Set<String> javaVendors) {
    return new TerracottaCommandLineEnvironment(javaVersion, javaVendors, javaOpts);
  }

  public TerracottaCommandLineEnvironment withJavaVendor(String javaVendor) {
    return new TerracottaCommandLineEnvironment(javaVersion, Collections.singleton(javaVendor), javaOpts);
  }

  public TerracottaCommandLineEnvironment withJavaOpts(List<String> javaOpts) {
    return new TerracottaCommandLineEnvironment(javaVersion, javaVendors, javaOpts);
  }

  @Override
  public String toString() {
    return "TerracottaCommandLineEnvironment{" +
           "javaVersion='" + javaVersion + '\'' +
           ", javaVendors=" + javaVendors +
           ", javaOpts=" + javaOpts +
           '}';
  }
}
