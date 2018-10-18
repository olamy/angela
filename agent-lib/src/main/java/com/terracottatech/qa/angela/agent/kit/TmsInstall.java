package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.distribution.Distribution;

import java.io.File;


/**
 * Installation instance of a TerracottaManagementServer
 */
public class TmsInstall {

  private final Distribution distribution;
  private final File installLocation;
  private final TerracottaCommandLineEnvironment tcEnv;
  private TerracottaManagementServerInstance terracottaManagementServerInstance;

  public File getInstallLocation() {
    return installLocation;
  }

  public TmsInstall(Distribution distribution, File location, TerracottaCommandLineEnvironment tcEnv) {
    this.distribution = distribution;
    this.installLocation = location;
    this.tcEnv = tcEnv;
    addTerracottaManagementServer();
  }

  public void addTerracottaManagementServer() {
    terracottaManagementServerInstance = new TerracottaManagementServerInstance(distribution.createDistributionController(), installLocation, tcEnv);
  }

  public TerracottaManagementServerInstance getTerracottaManagementServerInstance() {
    return terracottaManagementServerInstance;
  }

  public void removeServer() {
    terracottaManagementServerInstance = null;
  }

}
