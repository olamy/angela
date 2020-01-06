package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.dynamic_cluster.Stripe;
import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class DynamicConfigProvider implements ConfigurationProvider {
  private final String clusterName;
  private final List<Stripe> stripes;

  private DynamicConfigProvider(String clusterName, List<Stripe> stripes) {
    this.clusterName = clusterName;
    this.stripes = stripes;
  }

  public static DynamicConfigProvider dynamicCluster(String clusterName, Stripe... stripes) {
    return new DynamicConfigProvider(requireNonNull(clusterName), Arrays.asList(stripes));
  }

  @Override
  public List<TerracottaServer> getServers() {
    List<TerracottaServer> resServer = new ArrayList<>();
    for (Stripe stripe : stripes) {
      resServer.addAll(stripe.getTerracottaServerList());
    }
    return resServer;
  }

  @Override
  public TerracottaServer findServer(ServerSymbolicName serverSymbolicName) {
    for (Stripe stripe : stripes) {
      for (TerracottaServer terracottaServer : stripe.getTerracottaServerList()) {
        if (terracottaServer.getServerSymbolicName().equals(serverSymbolicName)) {
          return terracottaServer;
        }
      }
    }
    return null;
  }

  @Override
  public TerracottaServer findServer(int stripeId, int serverIndex) {
    //TODO: TDB-4771
    return null;
  }

  @Override
  public int findStripeIdOf(ServerSymbolicName serverSymbolicName) {
    //TODO: TDB-4771
    return -1;
  }

  @Override
  public Collection<String> getServersHostnames() {
    List<String> res = new ArrayList<>();
    for (Stripe stripe : stripes) {
      for (TerracottaServer terracottaServer : stripe.getTerracottaServerList()) {
        res.add(terracottaServer.getHostname());
      }
    }
    return res;
  }

  @Override
  public void createLinks(TerracottaServer terracottaServer,
                          DisruptionProvider disruptionProvider,
                          Map<ServerSymbolicName, Disruptor> disruptionLinks,
                          Map<String, Integer> proxiedPorts) {
    // Create network disruption links
  }
}
