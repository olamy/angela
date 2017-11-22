package com.terracottatech.qa.angela.agent;

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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class AgentControl {

  private final static Logger logger = LoggerFactory.getLogger(AgentControl.class);

  private final Map<String, TerracottaInstall> kitsInstalls = new HashMap<>();
  private final Ignite ignite;

  AgentControl(Ignite ignite) {
    this.ignite = ignite;
  }

  public void install(Topology topology, boolean offline, License license, int tcConfigIndex) {
    if (kitsInstalls.containsKey(topology.getId())) {
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

      kitsInstalls.put(topology.getId(), new TerracottaInstall(kitDir, topology));
    }
  }

  public void uninstall(Topology topology) {
    TerracottaInstall terracottaInstall = kitsInstalls.remove(topology.getId());
    if (terracottaInstall != null) {
      try {
        logger.info("Uninstalling kit for " + topology);
        Files.walk(Paths.get(terracottaInstall.getInstallLocation().getAbsolutePath()), FileVisitOption.FOLLOW_LINKS)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
      } catch (IOException ioe) {
        throw new RuntimeException("Unable to uninstall kit at " + terracottaInstall.getInstallLocation().getAbsolutePath(), ioe);
      }
    } else {
      logger.info("No installed kit for " + topology);
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
