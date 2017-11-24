package com.terracottatech.qa.angela.agent;

import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.kit.KitManager;
import com.terracottatech.qa.angela.common.kit.TerracottaInstall;
import com.terracottatech.qa.angela.common.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class AgentControl {

  private final static Logger logger = LoggerFactory.getLogger(AgentControl.class);

  private final Map<InstanceId, TerracottaInstall> kitsInstalls = new HashMap<>();
  private final Ignite ignite;

  AgentControl(Ignite ignite) {
    this.ignite = ignite;
  }

  public void install(InstanceId instanceId, Topology topology, boolean offline, License license, int tcConfigIndex) {
    if (kitsInstalls.containsKey(instanceId)) {
      logger.info("kit for " + topology + " already installed");
    } else {
      logger.info("Installing kit for " + topology);
      KitManager kitManager = topology.createKitManager();
      File kitDir = kitManager.installKit(license, offline);

      logger.info("Installing the tc-configs");
      for (TcConfig tcConfig : topology.getTcConfigs()) {
        tcConfig.updateLogsLocation(kitDir, tcConfigIndex);
        tcConfig.writeTcConfigFile(kitDir);
      }

      kitsInstalls.put(instanceId, new TerracottaInstall(kitDir, topology));
    }
  }

  public void uninstall(InstanceId instanceId, Topology topology) {
    TerracottaInstall terracottaInstall = kitsInstalls.remove(instanceId);
    if (terracottaInstall != null) {
      try {
        logger.info("Uninstalling kit for " + topology);
        KitManager kitManager = topology.createKitManager();
        // TODO : get log files

        kitManager.deleteInstall(terracottaInstall.getInstallLocation());
      } catch (IOException ioe) {
        throw new RuntimeException("Unable to uninstall kit at " + terracottaInstall.getInstallLocation().getAbsolutePath(), ioe);
      }
    } else {
      logger.info("No installed kit for " + topology);
    }
  }

  public TerracottaServerState start(final InstanceId instanceId, final TerracottaServer terracottaServer) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId)
        .getTerracottaServerInstance(terracottaServer);
    return serverInstance.start();
  }

  public TerracottaServerState stop(final InstanceId instanceId, final TerracottaServer terracottaServer) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId)
        .getTerracottaServerInstance(terracottaServer);
    return serverInstance.stop();
  }

  public void configureLicense(final InstanceId instanceId, final TerracottaServer terracottaServer, final License license, final TcConfig[] tcConfigs) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId)
        .getTerracottaServerInstance(terracottaServer);
    serverInstance.configureLicense(instanceId, license, tcConfigs);

  }
}
