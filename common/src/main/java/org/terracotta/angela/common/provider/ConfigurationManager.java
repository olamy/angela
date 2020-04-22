/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.provider;

import org.terracotta.angela.common.net.DisruptionProvider;
import org.terracotta.angela.common.net.Disruptor;
import org.terracotta.angela.common.net.PortAllocator;
import org.terracotta.angela.common.tcconfig.ServerSymbolicName;
import org.terracotta.angela.common.tcconfig.TerracottaServer;

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
                             Map<ServerSymbolicName, Disruptor> disruptionLinks, Map<ServerSymbolicName, Integer> proxiedPorts,
                             PortAllocator portAllocator);
}
