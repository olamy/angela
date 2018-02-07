package com.terracottatech.qa.angela.common.tcconfig;

public class NamedSecurityRootDirectory {
  private final ServerSymbolicName serverSymbolicName;
  private final SecurityRootDirectory securityRootDirectory;

  public static NamedSecurityRootDirectory withSecurityFor(ServerSymbolicName serverSymbolicName,
                                                           SecurityRootDirectory securityRootDirectory) {
    return new NamedSecurityRootDirectory(serverSymbolicName, securityRootDirectory);
  }


  private NamedSecurityRootDirectory(ServerSymbolicName serverSymbolicName, SecurityRootDirectory securityRootDirectory) {
    this.serverSymbolicName = serverSymbolicName;
    this.securityRootDirectory = securityRootDirectory;
  }

  public ServerSymbolicName getServerSymbolicName() {
    return serverSymbolicName;
  }

  public SecurityRootDirectory getSecurityRootDirectory() {
    return securityRootDirectory;
  }
}
