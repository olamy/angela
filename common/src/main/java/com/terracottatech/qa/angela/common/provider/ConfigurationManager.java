package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ConfigurationManager {
  void addStripe(TerracottaServer... newServers);

  void removeStripe(int stripeIndex);

  int getStripeIndexOf(ServerSymbolicName serverSymbolicName);

  List<List<TerracottaServer>> getStripes();

  void addServer(int stripeIndex, TerracottaServer newServer);

  void removeServer(int stripeIndex, int serverIndex);

  TerracottaServer getServer(int stripeIndex, int serverIndex);

  TerracottaServer getServer(ServerSymbolicName serverSymbolicName);

  List<TerracottaServer> getServers();

  Collection<String> getServersHostnames();

  void createDisruptionLinks(TerracottaServer terracottaServer, DisruptionProvider disruptionProvider,
                             Map<ServerSymbolicName, Disruptor> disruptionLinks, Map<String, Integer> proxiedPorts);
}
