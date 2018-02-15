package com.terracottatech.qa.angela.common.tms.security.config;

public class TmsServerSecurityConfig {

  private final String securityRootDirectory;
  private final String securityLevel;

  public TmsServerSecurityConfig(String securityRootDirectory, String securityLevel) {
    this.securityRootDirectory = securityRootDirectory;
    this.securityLevel = securityLevel;
  }

  public String getSecurityRootDirectory() {
    return securityRootDirectory != null ? securityRootDirectory : "";
  }

  public String getSecurityLevel() {
    return securityLevel != null ? securityLevel : "";
  }

}
