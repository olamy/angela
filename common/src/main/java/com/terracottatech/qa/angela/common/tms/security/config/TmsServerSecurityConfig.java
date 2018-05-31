package com.terracottatech.qa.angela.common.tms.security.config;

import java.nio.file.Path;

public class TmsServerSecurityConfig {

  private final Path tmsSecurityRootDirectory;
  private final Path clusterSecurityRootDirectory;

  public TmsServerSecurityConfig(Path tmsSecurityRootDirectory, Path clusterSecurityRootDirectory) {
    this.tmsSecurityRootDirectory = tmsSecurityRootDirectory;
    this.clusterSecurityRootDirectory = clusterSecurityRootDirectory;
  }

  public Path getTmsSecurityRootDirectory() {
    return tmsSecurityRootDirectory;
  }

  public Path getClusterSecurityRootDirectory() {
    return clusterSecurityRootDirectory;
  }
}
