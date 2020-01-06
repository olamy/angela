package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ConfigurationProvider {
  List<TerracottaServer> getServers();

  TerracottaServer findServer(ServerSymbolicName serverSymbolicName);

  TerracottaServer findServer(int stripeId, int serverIndex);

  int findStripeIdOf(ServerSymbolicName serverSymbolicName);

  Collection<String> getServersHostnames();

  void createLinks(TerracottaServer terracottaServer,
                   DisruptionProvider disruptionProvider,
                   Map<ServerSymbolicName, Disruptor> disruptionLinks,
                   Map<String, Integer> proxiedPorts);

}
