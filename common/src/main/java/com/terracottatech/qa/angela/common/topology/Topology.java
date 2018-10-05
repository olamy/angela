package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.distribution.Distribution102Controller;
import com.terracottatech.qa.angela.common.distribution.Distribution43Controller;
import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        tcConfig.createOrUpdateTcProperty("l1redirect.enabled", "false");
      }
    }
    checkConfigsHaveNoSymbolicNameDuplicate(tcConfigs);
  }

  private void checkConfigsHaveNoSymbolicNameDuplicate(TcConfig[] tcConfigs) {
    Set<ServerSymbolicName> names = new HashSet<>();
    for (TcConfig tcConfig : tcConfigs) {
      Set<ServerSymbolicName> serverSymbolicNames = tcConfig.getServers().keySet();
      serverSymbolicNames.forEach(serverSymbolicName -> {
        if (names.contains(serverSymbolicName)) {
          throw new IllegalArgumentException("Duplicate name found in TC configs : " + serverSymbolicName);
        }
      });
      names.addAll(serverSymbolicNames);
    }
  }

  public DistributionController createDistributionController() {
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

  public TcConfig getStripeConfig(int stripeId) {
    return this.tcConfigs[stripeId];
  }

  public TcConfig findTcConfigOf(ServerSymbolicName serverSymbolicName) {
    for (TcConfig tcConfig : this.tcConfigs) {
      if (tcConfig.getServers().keySet().contains(serverSymbolicName)) {
        return tcConfig;
      }
    }
    return null;
  }

  public int findStripeIdOf(ServerSymbolicName serverSymbolicName) {
    for (int i = 0; i < tcConfigs.length; i++) {
      TcConfig tcConfig = tcConfigs[i];
      if (tcConfig.getServers().keySet().contains(serverSymbolicName)) {
        return i;
      }
    }
    return -1;
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

  public Distribution getDistribution() {
    return distribution;
  }

  @Override
  public String toString() {
    return "Topology{" +
           "distribution=" + distribution +
           ", tcConfigs=" + Arrays.toString(tcConfigs) +
           ", netDisruptionEnabled=" + netDisruptionEnabled +
           '}';
  }
}
