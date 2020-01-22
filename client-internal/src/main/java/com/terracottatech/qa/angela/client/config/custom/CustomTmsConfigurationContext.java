package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.TmsConfigurationContext;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;

public class CustomTmsConfigurationContext implements TmsConfigurationContext {
  private Distribution distribution;
  private License license;
  private String hostname;
  private TmsServerSecurityConfig securityConfig;
  private TerracottaCommandLineEnvironment terracottaCommandLineEnvironment = TerracottaCommandLineEnvironment.DEFAULT;

  protected CustomTmsConfigurationContext() {
  }

  @Override
  public Distribution getDistribution() {
    return distribution;
  }

  public CustomTmsConfigurationContext distribution(Distribution distribution) {
    this.distribution = distribution;
    return this;
  }

  @Override
  public License getLicense() {
    return license;
  }

  public CustomTmsConfigurationContext license(License license) {
    this.license = license;
    return this;
  }

  @Override
  public String getHostname() {
    return hostname;
  }

  public CustomTmsConfigurationContext hostname(String serverName) {
    this.hostname = serverName;
    return this;
  }

  @Override
  public TmsServerSecurityConfig getSecurityConfig() {
    return securityConfig;
  }

  public CustomTmsConfigurationContext securityConfig(TmsServerSecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
    return this;
  }

  @Override
  public TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment() {
    return terracottaCommandLineEnvironment;
  }

  public CustomTmsConfigurationContext terracottaCommandLineEnvironment(TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.terracottaCommandLineEnvironment = terracottaCommandLineEnvironment;
    return this;
  }
}
