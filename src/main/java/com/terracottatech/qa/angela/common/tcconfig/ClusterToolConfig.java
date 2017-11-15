package com.terracottatech.qa.angela.common.tcconfig;

import java.io.Serializable;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterToolConfig implements Serializable {

  private final String hostname;
  private final String serverSymbolicName;
  private final LicenseConfig licenseConfig;

  public ClusterToolConfig(final String hostname, LicenseConfig licenseConfig) {
    this.hostname = hostname;
    this.serverSymbolicName = "clustertool:" + hostname;
    this.licenseConfig = licenseConfig;
  }

  public static ClusterToolConfig clustertool(final String hostname, LicenseConfig licenseConfig) {
    return new ClusterToolConfig(hostname, licenseConfig);
  }

  public String getHostname() {
    return hostname;
  }

  public String getServerSymbolicName() {
    return this.serverSymbolicName;
  }

  public LicenseConfig getLicenseConfig() {
    return licenseConfig;
  }
}
