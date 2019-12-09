package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.Collection;
import java.util.List;

public interface ConfigurationProvider {
  List<TerracottaServer> getServers();

  TerracottaServer findServer(ServerSymbolicName serverSymbolicName);

  TerracottaServer findServer(int stripeId, int serverIndex);

  int findStripeIdOf(ServerSymbolicName serverSymbolicName);

  Collection<String> getServersHostnames();
}
