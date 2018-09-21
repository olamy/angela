package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.Topology;

public class CustomTsaConfigurationContext implements TsaConfigurationContext {
  private Topology topology;
  private License license;
  private TerracottaCommandLineEnvironment terracottaCommandLineEnvironment = new TerracottaCommandLineEnvironment(CustomConfigurationContext.DEFAULT_JDK_VERSION, CustomConfigurationContext.DEFAULT_ALLOWED_JDK_VENDORS, null);

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
  public TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment() {
    return terracottaCommandLineEnvironment;
  }

  public CustomTsaConfigurationContext terracottaCommandLineEnvironment(TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.terracottaCommandLineEnvironment = terracottaCommandLineEnvironment;
    return this;
  }
}
