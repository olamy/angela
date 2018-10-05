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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds the test environment topology:
 * - Tc Config that represents the Terracotta cluster
 * - List of nodes where the test instances will run
 * - Version of the Terracotta installation
 */

public class Topology {
  private final Distribution distribution;
  private final List<TcConfig> tcConfigs;
  private final boolean netDisruptionEnabled;


  public Topology(Distribution distribution, TcConfig tcConfig, TcConfig... tcConfigs) {
    this(distribution, false, tcConfig, tcConfigs);
  }

  public Topology(Distribution distribution, boolean netDisruptionEnabled, TcConfig tcConfig, TcConfig... tcConfigs) {
    this.distribution = distribution;
    this.netDisruptionEnabled = netDisruptionEnabled;
    this.tcConfigs = new ArrayList<>();
    this.tcConfigs.add(Objects.requireNonNull(tcConfig));
    this.tcConfigs.addAll(Arrays.asList(tcConfigs));
    if (netDisruptionEnabled) {
      for (TcConfig cfg : new ArrayList<TcConfig>()) {
        cfg.createOrUpdateTcProperty("topology.validate", "false");
        cfg.createOrUpdateTcProperty("l1redirect.enabled", "false");
      }
    }
    checkConfigsHaveNoSymbolicNameDuplicate(tcConfigs);
  }

  private void checkConfigsHaveNoSymbolicNameDuplicate(TcConfig[] tcConfigs) {
    Set<ServerSymbolicName> names = new HashSet<>();
    for (TcConfig tcConfig : tcConfigs) {
      tcConfig.getServers().forEach(server -> {
        ServerSymbolicName serverSymbolicName = server.getServerSymbolicName();
        if (names.contains(serverSymbolicName)) {
          throw new IllegalArgumentException("Duplicate name found in TC configs : " + server);
        } else {
          names.add(serverSymbolicName);
        }
      });
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

  public Collection<TerracottaServer> getServers() {
    List<TerracottaServer> servers = new ArrayList<>();
    for (TcConfig tcConfig : this.tcConfigs) {
      servers.addAll(tcConfig.getServers());
    }
    return servers;
  }

  public TerracottaServer findServer(int stripeId, int serverIndex) {
    if (stripeId >= tcConfigs.size()) {
      throw new IllegalArgumentException("No such stripe #" + stripeId + " (there are: " + tcConfigs.size() + ")");
    }
    List<TerracottaServer> servers = tcConfigs.get(stripeId).getServers();
    if (serverIndex >= servers.size()) {
      throw new IllegalArgumentException("No such server #" + serverIndex + " (there are: " + servers.size() + " in stripe " + stripeId + ")");
    }
    return servers.get(serverIndex);
  }

  public TcConfig findTcConfigOf(ServerSymbolicName serverSymbolicName) {
    for (TcConfig tcConfig : this.tcConfigs) {
      for (TerracottaServer server : tcConfig.getServers()) {
        if (server.getServerSymbolicName().equals(serverSymbolicName)) {
          return tcConfig;
        }
      }
    }
    return null;
  }

  public int findStripeIdOf(ServerSymbolicName serverSymbolicName) {
    for (int i = 0; i < tcConfigs.size(); i++) {
      TcConfig tcConfig = tcConfigs.get(i);
      for (TerracottaServer server : tcConfig.getServers()) {
        if (server.getServerSymbolicName().equals(serverSymbolicName)) {
          return i;
        }
      }
    }
    return -1;
  }

  public List<TcConfig> getTcConfigs() {
    return this.tcConfigs;
  }

  public Collection<String> getServersHostnames() {
    return getServers().stream().map(TerracottaServer::getHostname).collect(Collectors.toList());
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
           ", tcConfigs=" + tcConfigs +
           ", netDisruptionEnabled=" + netDisruptionEnabled +
           '}';
  }
}
