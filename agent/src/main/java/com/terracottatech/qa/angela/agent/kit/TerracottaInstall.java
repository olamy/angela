package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
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
  //  private final NetworkController networkController;
  private final Map<ServerSymbolicName, TerracottaServerInstance> terracottaServerInstances = new HashMap<>();
  private TerracottaManagementServerInstance terracottaManagementServerInstance;

  public TerracottaInstall(final Topology topology, TerracottaServer terracottaServer, File location) {
    this.topology = topology;
    this.installLocation = location;
    addServer(terracottaServer);
//    this.networkController = networkController;
  }

  public TerracottaServerInstance getTerracottaServerInstance(TerracottaServer terracottaServer) {
    return terracottaServerInstances.get(terracottaServer.getServerSymbolicName());
  }

  public File getInstallLocation() {
    return installLocation;
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

  public TerracottaInstall(final Topology topology, File location) {
    this.topology = topology;
    this.installLocation = location;
    addTerracottaManagementServer();
//    this.networkController = networkController;
  }

  public void addTerracottaManagementServer() {
    terracottaManagementServerInstance = new TerracottaManagementServerInstance(topology.createDistributionController(), installLocation);
  }

  public TerracottaManagementServerInstance getTerracottaManagementServerInstance() {
    return terracottaManagementServerInstance;
  }

}
