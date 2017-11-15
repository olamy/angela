package com.terracottatech.qa.angela.kit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.tcconfig.TcConfig;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.topology.Topology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * Installation instance of a Terracotta server
 */
public class TerracottaInstall {

  private static final Logger logger = LoggerFactory.getLogger(TerracottaInstall.class);

  private final Topology topology;
  //  private final NetworkController networkController;
  private final Map<String, TerracottaServerInstance> terracottaServerInstances;


  public TerracottaInstall(final File location, final Topology topology) {
    this.topology = topology;
    this.terracottaServerInstances = createTerracottaServerInstancesMap(topology.getTcConfigs(), topology.createDistributionController(), location);
//    this.networkController = networkController;
  }

  private static Map<String, TerracottaServerInstance> createTerracottaServerInstancesMap(
      final TcConfig[] tcConfigs, final DistributionController distributionController, final File location) {
    Map<String, TerracottaServerInstance> terracottaServerInstances = new HashMap<>();
    for (TcConfig tcConfig : tcConfigs) {
      Map<String, TerracottaServer> servers = tcConfig.getServers();
      for (TerracottaServer terracottaServer : servers.values()) {
        terracottaServerInstances.put(terracottaServer.getServerSymbolicName(), new TerracottaServerInstance(terracottaServer.getServerSymbolicName(), distributionController, location));
      }
    }
    return terracottaServerInstances;
  }

  public TerracottaServerInstance getTerracottaServerInstance(final TerracottaServer terracottaServer) {
    return terracottaServerInstances.get(terracottaServer.getServerSymbolicName());
  }
}
