package com.terracottatech.qa.angela.client.config;

import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.Topology;

public interface TsaConfigurationContext {
  Topology getTopology();

  License getLicense();

  String getClusterName();

  TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment(String whatFor);

  interface TerracottaCommandLineEnvironmentKeys {
    String CLUSTER_TOOL = "ClusterTool";
    String SERVER_START_PREFIX = "Server-";
    String SERVER_STOP_PREFIX = "Stop-";
  }
}
