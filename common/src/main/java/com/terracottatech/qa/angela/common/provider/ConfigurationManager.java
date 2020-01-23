package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ConfigurationManager {
  void addStripe(TerracottaServer... newServers);

  void removeStripe(int stripeIndex);

  int getStripeIndexOf(UUID serverId);

  List<List<TerracottaServer>> getStripes();

  void addServer(int stripeIndex, TerracottaServer newServer);

  void removeServer(int stripeIndex, int serverIndex);

  TerracottaServer getServer(int stripeIndex, int serverIndex);

  TerracottaServer getServer(UUID serverId);

  List<TerracottaServer> getServers();

  Collection<String> getServersHostnames();

  void createDisruptionLinks(TerracottaServer terracottaServer, DisruptionProvider disruptionProvider,
                             Map<ServerSymbolicName, Disruptor> disruptionLinks, Map<ServerSymbolicName, Integer> proxiedPorts);
}
