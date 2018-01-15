package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.distribution.Distribution102Controller;
import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * Installation instance of a TerracottaManagementServer
 */
public class TmsInstall {

  private final Distribution distribution;
  private final File installLocation;
  private TerracottaManagementServerInstance terracottaManagementServerInstance;

  public File getInstallLocation() {
    return installLocation;
  }

  public TmsInstall(Distribution distribution, File location) {
    this.distribution = distribution;
    this.installLocation = location;
    addTerracottaManagementServer();
//    this.networkController = networkController;
  }

  public void addTerracottaManagementServer() {
    terracottaManagementServerInstance = new TerracottaManagementServerInstance(createDistributionController(distribution), installLocation);
  }

  public TerracottaManagementServerInstance getTerracottaManagementServerInstance() {
    return terracottaManagementServerInstance;
  }

  public DistributionController createDistributionController(Distribution distribution) {
    if (distribution.getVersion().getMajor() == 10) {
      if (distribution.getVersion().getMinor() > 0) {
        return new Distribution102Controller(distribution, null);
      }
    }
    throw new IllegalArgumentException("Version not supported : " + this.distribution.getVersion());
  }

  public void removeServer() {
    terracottaManagementServerInstance = null;
  }

}
