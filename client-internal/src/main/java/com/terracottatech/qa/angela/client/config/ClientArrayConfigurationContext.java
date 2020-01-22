package com.terracottatech.qa.angela.client.config;

import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;

public interface ClientArrayConfigurationContext {
  ClientArrayTopology getClientArrayTopology();

  License getLicense();

  TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment();
}
