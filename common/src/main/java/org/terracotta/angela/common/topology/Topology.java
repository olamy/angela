/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.topology;

import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.provider.ConfigurationManager;
import org.terracotta.angela.common.provider.TcConfigManager;
import org.terracotta.angela.common.tcconfig.TcConfig;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.tcconfig.TsaConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.terracotta.angela.common.provider.TcConfigManager.mergeTcConfigs;

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
