package com.terracottatech.qa.angela;

import org.apache.ignite.IgniteCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.kit.KitManager;
import com.terracottatech.qa.angela.kit.TerracottaInstall;
import com.terracottatech.qa.angela.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.kit.distribution.Distribution;
import com.terracottatech.qa.angela.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.tcconfig.ClusterToolConfig;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.topology.Topology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class AgentControl {

  Map<String, TerracottaInstall> kitsInstalls = new HashMap<>();

  private final static Logger logger = LoggerFactory.getLogger(TsaControl.class);

  public static final AgentControl agentControl = new AgentControl();


  public void init(Topology topology, boolean offline, ClusterToolConfig clusterToolConfig, int tcConfigIndex) {
    if (kitsInstalls.containsKey(topology.getId())) {
      System.out.println("Already exists");
    } else {
      logger.info("Installing the kit");
      KitManager kitManager = topology.createKitManager();
      File kitDir = kitManager.installKit(clusterToolConfig.getLicenseConfig(), offline);

      logger.info("Installing the tc-configs");
      topology.getTcConfigs()[tcConfigIndex].updateLogsLocation(kitDir, tcConfigIndex);
      topology.getTcConfigs()[tcConfigIndex].writeTcConfigFile(kitDir);

      kitsInstalls.put(topology.getId(), new TerracottaInstall(kitDir, topology));

      System.out.println("kitDir = " + kitDir.getAbsolutePath());
//        new TerracottaInstall(kitDir, clusterConfig, managementConfig, clusterToolConfig, clusterConfig.getVersion(), agent
//            .getNetworkController())
    }
  }

  public TerracottaServerInstance.TerracottaServerState start(final String topologyId, final TerracottaServer terracottaServer) {
    DistributionController distributionController = kitsInstalls.get(topologyId).getTopology().createDistributionController();
    return distributionController.start(terracottaServer, kitsInstalls.get(topologyId).getLocation());
  }
}
