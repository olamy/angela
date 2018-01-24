package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.topology.Version;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SecureTcConfig extends TcConfig {

  private final Map<ServerSymbolicName, SecurityRootDirectory> SecurityRootDirectoryMap = new HashMap<>();

  public static SecureTcConfig secureTcConfig(Version version,
                                              URL tcConfigPath,
                                              NamedSecurityRootDirectory... namedSecurityRootDirectories) {
    return new SecureTcConfig(version, tcConfigPath, true, namedSecurityRootDirectories);
  }

  public static SecureTcConfig secureTcConfig(Version version,
                                              URL tcConfigPath,
                                              boolean validateConfig,
                                              NamedSecurityRootDirectory... namedSecurityRootDirectories) {
    return new SecureTcConfig(version, tcConfigPath, validateConfig, namedSecurityRootDirectories);
  }

  private SecureTcConfig(Version version, URL tcConfigPath, boolean validateConfig, NamedSecurityRootDirectory... namedSecurityRootDirectories) {
    super(version, tcConfigPath);
    for (NamedSecurityRootDirectory namedSecurityRootDirectory : namedSecurityRootDirectories) {
      SecurityRootDirectoryMap.put(namedSecurityRootDirectory.getServerSymbolicName(),
                            namedSecurityRootDirectory.getSecurityRootDirectory());
    }
    if (validateConfig) {
      validateConfig();
    }

  }

  private void validateConfig() {
    for (ServerSymbolicName serverSymbolicName : getServers().keySet()) {
      if (!SecurityRootDirectoryMap.containsKey(serverSymbolicName)) {
        throw new IllegalArgumentException("NamedSecurityRootDirectory is not provided for server " +
                                           serverSymbolicName.getSymbolicName());
      }
    }

    if (SecurityRootDirectoryMap.size() != getServers().size()) {
      throw new IllegalArgumentException("Given NamedSecurityRootDirectory(s) contains extra servers " +
                                         "which are not present in tc-config, perhaps some server configurations " +
                                         "are missing from tc-config?");
    }
  }

  public SecurityRootDirectory securityRootDirectoryFor(ServerSymbolicName serverSymbolicName) {
    return SecurityRootDirectoryMap.get(serverSymbolicName);
  }

}
