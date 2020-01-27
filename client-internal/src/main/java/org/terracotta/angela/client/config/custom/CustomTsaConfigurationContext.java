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

package org.terracotta.angela.client.config.custom;

import org.terracotta.angela.client.config.TsaConfigurationContext;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.topology.Topology;

import java.util.HashMap;
import java.util.Map;

public class CustomTsaConfigurationContext implements TsaConfigurationContext {
  private Topology topology;
  private License license;
  private String clusterName;
  private final Map<String, TerracottaCommandLineEnvironment> terracottaCommandLineEnvironments = new HashMap<>();
  private TerracottaCommandLineEnvironment defaultTerracottaCommandLineEnvironment = TerracottaCommandLineEnvironment.DEFAULT;

  protected CustomTsaConfigurationContext() {
  }

  @Override
  public Topology getTopology() {
    return topology;
  }

  public CustomTsaConfigurationContext topology(Topology topology) {
    this.topology = topology;
    return this;
  }

  @Override
  public License getLicense() {
    return license;
  }

  public CustomTsaConfigurationContext license(License license) {
    this.license = license;
    return this;
  }

  @Override
  public String getClusterName() {
    return clusterName;
  }

  public CustomTsaConfigurationContext clusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  @Override
  public TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment(String key) {
    TerracottaCommandLineEnvironment tce = terracottaCommandLineEnvironments.get(key);
    return tce != null ? tce : defaultTerracottaCommandLineEnvironment;
  }

  public CustomTsaConfigurationContext terracottaCommandLineEnvironment(TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.defaultTerracottaCommandLineEnvironment = terracottaCommandLineEnvironment;
    return this;
  }

  public CustomTsaConfigurationContext terracottaCommandLineEnvironment(String key, TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.terracottaCommandLineEnvironments.put(key, terracottaCommandLineEnvironment);
    return this;
  }
}
