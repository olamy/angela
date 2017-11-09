package com.terracottatech.qa.angela.topology;

import com.terracottatech.qa.angela.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.tcconfig.TcConfig;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the test environment topology:
 * - Tc Config that represents the Terracotta cluster
 * - List of nodes where the test instances will run
 * - Version of the Terracotta installation
 */

public class Topology {
  private String id;
  private DistributionController distributionController;
  private TcConfig[] tcConfigList = null;

  public Topology(final String id, final DistributionController distributionController, final TcConfig... tcConfigs) {
    this.id = id;
    this.distributionController = distributionController;
    this.tcConfigList = new TcConfig[tcConfigs.length];
    for (int i = 0; i < tcConfigs.length; i++) {
      final TcConfig tcConfig = tcConfigs[i];
      tcConfig.initTcConfigHolder(distributionController.getVersion());
      this.tcConfigList[i] = tcConfig;
    }
  }

  public DistributionController getDistributionController() {
    return distributionController;
  }

  public Map<String, TerracottaServer> getServers() {
    Map<String, TerracottaServer> servers = new HashMap<String, TerracottaServer>();
    for (TcConfig tcConfig : this.tcConfigList) {
      servers.putAll(tcConfig.getServers());
    }
    return servers;
  }

  public TcConfig get(final int stripeId) {
    return this.tcConfigList[stripeId];
  }

  public TcConfig getTcConfig(final String serverSymbolicName) {
    for (TcConfig tcConfig : this.tcConfigList) {
      if (tcConfig.getServers().keySet().contains(serverSymbolicName)) {
        return tcConfig;
      }
    }
    return null;
  }

  public int getTcConfigIndex(final InetAddress inetAddress) throws UnknownHostException {
    for (int tcConfigIndex = 0; tcConfigIndex < this.tcConfigList.length; tcConfigIndex++) {
      final TcConfig tcConfig = this.tcConfigList[tcConfigIndex];
      Collection<TerracottaServer> servers = tcConfig.getServers().values();
      for (TerracottaServer server : servers) {
        if (inetAddress.toString().equalsIgnoreCase(InetAddress.getByName(server.getHostname()).toString())) {
          return tcConfigIndex;
        }
      }
    }
    return -1;
  }

  public List<String> getLogsLocation() {
    List<String> logsLocation = new ArrayList<String>();
    for (TcConfig tcConfig : this.tcConfigList) {
      logsLocation.addAll(tcConfig.getLogsLocation());
    }
    return logsLocation;
  }

  public TcConfig[] getTcConfigs() {
    return this.tcConfigList;
  }

  public String getId() {
    return id;
  }
}
