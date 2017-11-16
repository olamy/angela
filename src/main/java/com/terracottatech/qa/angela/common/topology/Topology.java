package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.agent.kit.KitManager;
import com.terracottatech.qa.angela.agent.kit.distribution.Distribution;
import com.terracottatech.qa.angela.agent.kit.distribution.Distribution102Controller;
import com.terracottatech.qa.angela.agent.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

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
  private final String id;
  private final Distribution distribution;
  private final TcConfig[] tcConfigs;

  public Topology(final String id, final Distribution distribution, final TcConfig... tcConfigs) {
    this.id = id;
    this.distribution = distribution;
    this.tcConfigs = tcConfigs;
  }

  public DistributionController createDistributionController() {
    if (distribution.getVersion().getMajor() == 10) {
      if (distribution.getVersion().getMinor() > 0) {
        return new Distribution102Controller(distribution, this);
      }
    }
    throw new IllegalArgumentException("Version not supported");
  }

  public KitManager createKitManager() {
    return new KitManager(distribution);
  }

  public Map<ServerSymbolicName, TerracottaServer> getServers() {
    Map<ServerSymbolicName, TerracottaServer> servers = new HashMap<>();
    for (TcConfig tcConfig : this.tcConfigs) {
      servers.putAll(tcConfig.getServers());
    }
    return servers;
  }

  public TcConfig get(final int stripeId) {
    return this.tcConfigs[stripeId];
  }

  public TcConfig getTcConfig(final ServerSymbolicName serverSymbolicName) {
    for (TcConfig tcConfig : this.tcConfigs) {
      if (tcConfig.getServers().keySet().contains(serverSymbolicName)) {
        return tcConfig;
      }
    }
    return null;
  }

  public int getTcConfigIndex(final InetAddress inetAddress) throws UnknownHostException {
    for (int tcConfigIndex = 0; tcConfigIndex < this.tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = this.tcConfigs[tcConfigIndex];
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
    for (TcConfig tcConfig : this.tcConfigs) {
      logsLocation.addAll(tcConfig.getLogsLocation());
    }
    return logsLocation;
  }

  public TcConfig[] getTcConfigs() {
    return this.tcConfigs;
  }

  public String getId() {
    return id;
  }

  public Collection<String> getServersHostnames() {
    List<String> hostnames = new ArrayList<>();
    Map<ServerSymbolicName, TerracottaServer> servers = getServers();
    for (TerracottaServer terracottaServer : servers.values()) {
      hostnames.add(terracottaServer.getHostname());
    }
    return hostnames;
  }

  public LicenseType getLicenseType() {
    return distribution.getLicenseType();
  }
}
