package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.Topology;

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
