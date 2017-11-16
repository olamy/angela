package com.terracottatech.qa.angela.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.kit.KitManager;
import com.terracottatech.qa.angela.agent.kit.TerracottaInstall;
import com.terracottatech.qa.angela.agent.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.client.TsaControl;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class AgentControl {

  private final Map<String, TerracottaInstall> kitsInstalls = new HashMap<>();

  private final static Logger logger = LoggerFactory.getLogger(TsaControl.class);

  public void init(Topology topology, boolean offline, License license, int tcConfigIndex) {
    if (kitsInstalls.containsKey(topology.getId())) {
      System.out.println("Already exists");
    } else {
      logger.info("Installing the kit");
      KitManager kitManager = topology.createKitManager();
      File kitDir = kitManager.installKit(license, offline);

      logger.info("Installing the tc-configs");
      topology.getTcConfigs()[tcConfigIndex].updateLogsLocation(kitDir, tcConfigIndex);
      topology.getTcConfigs()[tcConfigIndex].writeTcConfigFile(kitDir);

      kitsInstalls.put(topology.getId(), new TerracottaInstall(kitDir, topology));

      System.out.println("kitDir = " + kitDir.getAbsolutePath());
//        new TerracottaInstall(kitDir, clusterConfig, managementConfig, clusterToolConfig, clusterConfig.getVersion(), agent
//            .getNetworkController())
    }
  }

  public TerracottaServerState start(final String topologyId, final TerracottaServer terracottaServer) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(topologyId)
        .getTerracottaServerInstance(terracottaServer);
    return serverInstance.start();
  }

  public TerracottaServerState stop(final String topologyId, final TerracottaServer terracottaServer) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(topologyId)
        .getTerracottaServerInstance(terracottaServer);
    return serverInstance.stop();
  }

  public void configureLicense(final String topologyId, final TerracottaServer terracottaServer, final License license, final TcConfig[] tcConfigs) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(topologyId)
        .getTerracottaServerInstance(terracottaServer);
    serverInstance.configureLicense(topologyId, license, tcConfigs);

  }
}
