package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.provider.ConfigurationProvider;
import com.terracottatech.qa.angela.common.provider.TcConfigProvider;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.tcconfig.TsaConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.terracottatech.qa.angela.common.provider.TcConfigProvider.mergeTcConfigs;

/**
 * Holds the test environment topology:
 * - Tc Config that represents the Terracotta cluster
 * - List of nodes where the test instances will run
 * - Version of the Terracotta installation
 */

public class Topology {
  private final Distribution distribution;
  private final boolean netDisruptionEnabled;
  private final ConfigurationProvider configurationProvider;

  public Topology(Distribution distribution, TsaConfig tsaConfig) {
    this(distribution, false, tsaConfig.getTcConfigs());
  }

  public Topology(Distribution distribution, boolean netDisruptionEnabled, TsaConfig tsaConfig) {
    this(distribution, netDisruptionEnabled, tsaConfig.getTcConfigs());
  }

  public Topology(Distribution distribution, TcConfig[] tcConfigs) {
    this(distribution, false, Arrays.asList(tcConfigs));
  }
  public Topology(Distribution distribution, TcConfig tcConfig, TcConfig... tcConfigs) {
    this(distribution, false, mergeTcConfigs(tcConfig, tcConfigs));
  }

  public Topology(Distribution distribution, boolean netDisruptionEnabled, TcConfig[] tcConfigs) {
    this(distribution, netDisruptionEnabled, Arrays.asList(tcConfigs));
  }

  public Topology(Distribution distribution, boolean netDisruptionEnabled, TcConfig tcConfig, TcConfig... tcConfigs) {
    this(distribution, netDisruptionEnabled, mergeTcConfigs(tcConfig, tcConfigs));
  }

  public Topology(Distribution distribution, boolean netDisruptionEnabled, ConfigurationProvider configurationProvider) {
    this.distribution = distribution;
    this.netDisruptionEnabled = netDisruptionEnabled;
    this.configurationProvider = configurationProvider;
  }

  public Topology(Distribution distribution, ConfigurationProvider configurationProvider) {
    this(distribution, false, configurationProvider);
  }

  private Topology(Distribution distribution, boolean netDisruptionEnabled, List<TcConfig> tcConfigs) {
    this.distribution = distribution;
    this.netDisruptionEnabled = netDisruptionEnabled;
    this.configurationProvider = TcConfigProvider.withTcConfig(tcConfigs, netDisruptionEnabled);
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

  public ConfigurationProvider getConfigurationProvider() {
    return configurationProvider;
  }

  public Collection<TerracottaServer> getServers() {
    return configurationProvider.getServers();
  }

  public Collection<String> getServersHostnames() {
    return configurationProvider.getServersHostnames();
  }

  public TerracottaServer findServer(ServerSymbolicName serverSymbolicName) {
    return configurationProvider.findServer(serverSymbolicName);
  }
  public TerracottaServer findServer(int stripeId, int serverIndex) {
    return configurationProvider.findServer(stripeId, serverIndex);
  }

  public int findStripeIdOf(ServerSymbolicName serverSymbolicName) {
    return configurationProvider.findStripeIdOf(serverSymbolicName);
  }

  @Override
  public String toString() {
    return "Topology{" +
        "distribution=" + distribution +
        ", configurationProvider=" + configurationProvider +
        ", netDisruptionEnabled=" + netDisruptionEnabled +
        '}';
  }
}
