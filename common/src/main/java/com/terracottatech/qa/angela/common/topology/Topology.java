package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.distribution.Distribution102Controller;
import com.terracottatech.qa.angela.common.distribution.Distribution43Controller;
import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
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
  private final Distribution distribution;
  private final TcConfig[] tcConfigs;
  private final boolean netDisruptionEnabled;


  public Topology(final Distribution distribution, final TcConfig... tcConfigs) {
    this(distribution, false, tcConfigs);
  }

  public Topology(final Distribution distribution, final boolean netDisruptionEnabled, final TcConfig... tcConfigs) {
    this.distribution = distribution;
    this.netDisruptionEnabled = netDisruptionEnabled;
    this.tcConfigs = tcConfigs;
    if (netDisruptionEnabled) {
      for (TcConfig tcConfig : tcConfigs) {
        tcConfig.createOrUpdateTcProperty("topology.validate", "false");
      }
    }
  }

  public DistributionController createDistributionController(TcConfig tcConfig) {
    //TODO should it be validated early when constructing topology?
    if (distribution.getVersion().getMajor() == 10) {
      if (distribution.getVersion().getMinor() > 0) {
        return new Distribution102Controller(distribution, this);
      }
    } else if (netDisruptionEnabled) {
      throw new IllegalArgumentException("Network disruption not supported for older versions");
    } else if (distribution.getVersion().getMajor() == 4) {
      if (distribution.getVersion().getMinor() >= 3) {
        return new Distribution43Controller(distribution, this);
      }
    }
    throw new IllegalArgumentException("Version not supported : " + distribution.getVersion());
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

  public boolean isNetDisruptionEnabled() {
    return netDisruptionEnabled;
  }

  @Override
  public String toString() {
    return "Topology{" +
           "distribution=" + distribution +
           ", tcConfigs=" + Arrays.toString(tcConfigs) +
           ", netDisruptionEnabled=" + netDisruptionEnabled +
           '}';
  }

  public Distribution getDistribution() {
    return distribution;
  }
}
