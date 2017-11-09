package com.terracottatech.qa.angela.tcconfig;

import java.io.Serializable;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterToolConfig implements Serializable {

  private String hostname;
  private String serverSymbolicName;
  private LicenseConfig licenseConfig;

  public ClusterToolConfig(final String hostname) {
    this.hostname = hostname;
    this.serverSymbolicName = "clustertool:" + hostname;
  }

  public ClusterToolConfig(final String hostname, LicenseConfig licenseConfig) {
    this.hostname = hostname;
    this.serverSymbolicName = "clustertool:" + hostname;
    this.licenseConfig = licenseConfig;
  }

  public static ClusterToolConfig clustertool(final String hostname) {
    return new ClusterToolConfig(hostname);
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
