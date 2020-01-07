package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.provider.ConfigurationManager;
import com.terracottatech.qa.angela.common.provider.TcConfigManager;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.tcconfig.TsaConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.terracottatech.qa.angela.common.provider.TcConfigManager.mergeTcConfigs;

/**
 * Holds the test environment topology:
 * - Tc Config that represents the Terracotta cluster
 * - List of nodes where the test instances will run
 * - Version of the Terracotta installation
 */

public class Topology {
  private final Distribution distribution;
  private final boolean netDisruptionEnabled;
  private final ConfigurationManager configurationManager;

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

  public Topology(Distribution distribution, boolean netDisruptionEnabled, ConfigurationManager configurationManager) {
    this.distribution = distribution;
    this.netDisruptionEnabled = netDisruptionEnabled;
    this.configurationManager = configurationManager;
  }

  public Topology(Distribution distribution, ConfigurationManager configurationManager) {
    this(distribution, false, configurationManager);
  }

  private Topology(Distribution distribution, boolean netDisruptionEnabled, List<TcConfig> tcConfigs) {
    this.distribution = distribution;
    this.netDisruptionEnabled = netDisruptionEnabled;
    this.configurationManager = TcConfigManager.withTcConfig(tcConfigs, netDisruptionEnabled);
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

  public ConfigurationManager getConfigurationManager() {
    return configurationManager;
  }

  public int getStripeIdOf(ServerSymbolicName serverSymbolicName) {
    return configurationManager.getStripeIndexOf(serverSymbolicName);
  }

  public void addStripe(TerracottaServer... newServers) {
    configurationManager.addStripe(newServers);
  }

  public void removeStripe(int stripeIndex) {
    configurationManager.removeStripe(stripeIndex);
  }

  public List<List<TerracottaServer>> getStripes() {
    return configurationManager.getStripes();
  }

  public void addServer(int stripeIndex, TerracottaServer newServer) {
    configurationManager.addServer(stripeIndex, newServer);
  }

  public void removeServer(int stripeIndex, int serverIndex) {
    configurationManager.removeServer(stripeIndex, serverIndex);
  }

  public TerracottaServer getServer(ServerSymbolicName serverSymbolicName) {
    return configurationManager.getServer(serverSymbolicName);
  }

  public TerracottaServer getServer(int stripeIndex, int serverIndex) {
    return configurationManager.getServer(stripeIndex, serverIndex);
  }

  public List<TerracottaServer> getServers() {
    return configurationManager.getServers();
  }

  public Collection<String> getServersHostnames() {
    return configurationManager.getServersHostnames();
  }

  @Override
  public String toString() {
    return "Topology{" +
        "distribution=" + distribution +
        ", configurationManager=" + configurationManager +
        ", netDisruptionEnabled=" + netDisruptionEnabled +
        '}';
  }
}
