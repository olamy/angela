package com.terracottatech.qa.angela.client.net;

import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

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
