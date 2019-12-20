package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.dynamicconfig.Stripe;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DynamicConfigProvider implements ConfigurationProvider {
  private final String clusterName;
  private final List<Stripe> stripes;

  private DynamicConfigProvider(String clusterName, List<Stripe> stripes) {
    this.clusterName = clusterName;
    this.stripes = stripes;
  }

  public static DynamicConfigProvider withDynamicCluster(String clusterName, Stripe... stripes) {
    DynamicConfigProvider dynamicConfigProvider = new DynamicConfigProvider(clusterName, Arrays.asList(stripes));
    return dynamicConfigProvider;
  }


  @Override
  public List<TerracottaServer> getServers() {
    List<TerracottaServer> resServer = new ArrayList<>();
    for (Stripe stripe : stripes) {
      for (TerracottaServer terracottaServer : stripe.getTerracottaServerList()) {
        resServer.add(terracottaServer);
      }
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
    //TODO
    return null;
  }

  @Override
  public int findStripeIdOf(ServerSymbolicName serverSymbolicName) {
    //TODO
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
}
