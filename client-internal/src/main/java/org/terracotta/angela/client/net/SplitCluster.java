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

package org.terracotta.angela.client.net;

import org.terracotta.angela.common.tcconfig.ServerSymbolicName;
import org.terracotta.angela.common.tcconfig.TerracottaServer;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SplitCluster represents one more servers and it is subset of a stripe.
 * Servers to servers network disruption involves two or more split clusters.
 */
public class SplitCluster {

  private final Set<ServerSymbolicName> servers;

  public SplitCluster(TerracottaServer server) {
    this(Collections.singleton(server));

  }

  public SplitCluster(Collection<TerracottaServer> servers) {
    this.servers = Collections.unmodifiableSet(servers.stream()
        .map(TerracottaServer::getServerSymbolicName)
        .collect(Collectors.toSet()));
  }

  public Set<ServerSymbolicName> getServers() {
    return servers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SplitCluster that = (SplitCluster)o;

    return servers != null ? servers.equals(that.servers) : that.servers == null;
  }

  @Override
  public int hashCode() {
    return servers != null ? servers.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "SplitCluster{" +
           "servers=" + servers.stream()
               .map(ServerSymbolicName::getSymbolicName)
               .collect(Collectors.joining(",", "[", "]")) +
           '}';
  }
}
