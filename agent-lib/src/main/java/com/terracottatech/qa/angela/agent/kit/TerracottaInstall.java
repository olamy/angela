package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * Installation instance of a Terracotta server
 */
public class TerracottaInstall {

  private final Topology topology;
  private final File installLocation;
  private final File licenseFile;
  //  private final NetworkController networkController;
  private final Map<ServerSymbolicName, TerracottaServerInstance> terracottaServerInstances = new HashMap<>();

  public TerracottaInstall(final Topology topology, TerracottaServer terracottaServer, File location, String licenseFilename) {
    this.topology = topology;
    this.installLocation = location;
    this.licenseFile = new File(location, licenseFilename);
    addServer(terracottaServer);
//    this.networkController = networkController;
  }

  public TerracottaServerInstance getTerracottaServerInstance(TerracottaServer terracottaServer) {
    synchronized (terracottaServerInstances) {
      return terracottaServerInstances.get(terracottaServer.getServerSymbolicName());
    }
  }

  public File getInstallLocation() {
    return installLocation;
  }

  public File getLicenseFileLocation() {
    return licenseFile;
  }

  public void addServer(TerracottaServer terracottaServer) {
    synchronized (terracottaServerInstances) {
      terracottaServerInstances.put(terracottaServer.getServerSymbolicName(), new TerracottaServerInstance(terracottaServer.getServerSymbolicName(), topology.createDistributionController(), installLocation));
    }
  }

  public synchronized int removeServer(TerracottaServer terracottaServer) {
    synchronized (terracottaServerInstances) {
      terracottaServerInstances.remove(terracottaServer.getServerSymbolicName());
      return terracottaServerInstances.size();
    }
  }

  public synchronized int numberOfTerracottaInstances() {
    synchronized (terracottaServerInstances) {
      return terracottaServerInstances.size();
    }
  }

}
