package com.terracottatech.qa.angela.client.config;

import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;

public interface TmsConfigurationContext {
  Distribution getDistribution();

  License getLicense();

  String getHostname();

  TmsServerSecurityConfig getSecurityConfig();

  TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment();
}
