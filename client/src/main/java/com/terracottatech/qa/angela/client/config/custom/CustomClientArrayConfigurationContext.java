package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.ClientArrayConfigurationContext;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;

public class CustomClientArrayConfigurationContext implements ClientArrayConfigurationContext {
  private ClientArrayTopology clientArrayTopology;
  private License license;
  private TerracottaCommandLineEnvironment terracottaCommandLineEnvironment = new TerracottaCommandLineEnvironment(CustomConfigurationContext.DEFAULT_JDK_VERSION, CustomConfigurationContext.DEFAULT_ALLOWED_JDK_VENDORS, null);

  protected CustomClientArrayConfigurationContext() {
  }

  @Override
  public ClientArrayTopology getClientArrayTopology() {
    return clientArrayTopology;
  }

  public CustomClientArrayConfigurationContext clientArrayTopology(ClientArrayTopology clientArrayTopology) {
    this.clientArrayTopology = clientArrayTopology;
    return this;
  }

  @Override
  public License getLicense() {
    return license;
  }

  public CustomClientArrayConfigurationContext license(License license) {
    this.license = license;
    return this;
  }

  @Override
  public TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment() {
    return terracottaCommandLineEnvironment;
  }

  public CustomClientArrayConfigurationContext terracottaCommandLineEnvironment(TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.terracottaCommandLineEnvironment = terracottaCommandLineEnvironment;
    return this;
  }
}
