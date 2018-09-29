package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
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

  public TerracottaInstall(final Topology topology, File location, String licenseFilename) {
    this.topology = topology;
    this.installLocation = location;
    this.licenseFile = new File(location, licenseFilename);
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

  public void addServer(TerracottaServer terracottaServer, TcConfig tcConfig) {
    synchronized (terracottaServerInstances) {
      terracottaServerInstances.put(terracottaServer.getServerSymbolicName(), new TerracottaServerInstance(terracottaServer.getServerSymbolicName(), topology.createDistributionController(), installLocation, tcConfig, topology.isNetDisruptionEnabled()));
    }
  }

  public synchronized int removeServer(TerracottaServer terracottaServer) {
    synchronized (terracottaServerInstances) {
      TerracottaServerInstance instance = terracottaServerInstances.remove(terracottaServer.getServerSymbolicName());
      if (instance != null){
        instance.close();
      }
      return terracottaServerInstances.size();
    }
  }

  public synchronized int numberOfTerracottaInstances() {
    synchronized (terracottaServerInstances) {
      return terracottaServerInstances.size();
    }
  }

}
