package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.dynamic_cluster.Stripe;
import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.net.PortChooser;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DynamicConfigManager implements ConfigurationManager {
  private final List<Stripe> stripes;

  private DynamicConfigManager(Stripe... stripes) {
    if (stripes == null || stripes.length == 0) {
      throw new IllegalArgumentException("Stripe list cannot be null or empty");
    }
    this.stripes = new ArrayList<>(Arrays.asList(stripes));
  }

  public static DynamicConfigManager dynamicCluster(Stripe... stripes) {
    return new DynamicConfigManager(stripes);
  }

  @Override
  public int getStripeIndexOf(UUID serverId) {
    for (int i = 0; i < stripes.size(); i++) {
      for (TerracottaServer server : stripes.get(i).getServers()) {
        if (server.getId().equals(serverId)) {
          return i;
        }
      }
    }
    return -1;
  }

  @Override
  public void addStripe(TerracottaServer... newServers) {
    stripes.add(Stripe.stripe(newServers));
  }

  @Override
  public void removeStripe(int stripeIndex) {
    if (stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("No such stripe #" + stripeIndex + " (there are: " + stripes.size() + ")");
    }
    stripes.remove(stripeIndex);
  }

  @Override
  public List<List<TerracottaServer>> getStripes() {
    List<List<TerracottaServer>> stripeList = new ArrayList<>();
    for (Stripe stripe : stripes) {
      stripeList.add(new ArrayList<>(stripe.getServers()));
    }
    return stripeList;
  }

  @Override
  public void addServer(int stripeIndex, TerracottaServer newServer) {
    if (stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("No such stripe #" + stripeIndex + " (there are: " + stripes.size() + ")");
    }
    stripes.get(stripeIndex).addServer(newServer);
  }

  @Override
  public void removeServer(int stripeIndex, int serverIndex) {
    if (stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("No such stripe #" + stripeIndex + " (there are: " + stripes.size() + ")");
    }
    List<TerracottaServer> servers = stripes.get(stripeIndex).getServers();
    if (serverIndex >= servers.size()) {
      throw new IllegalArgumentException("No such server #" + serverIndex + " (there are: " + servers.size() + " in stripe " + stripeIndex + ")");
    }
    Stripe stripe = stripes.get(stripeIndex);
    stripe.removeServer(serverIndex);

    // Remove stripe if the only server in it was removed
    if (stripe.getServers().size() == 0) {
      stripes.remove(stripeIndex);
    }
  }

  @Override
  public TerracottaServer getServer(int stripeIndex, int serverIndex) {
    if (stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("No such stripe #" + stripeIndex + " (there are: " + stripes.size() + ")");
    }
    List<TerracottaServer> servers = stripes.get(stripeIndex).getServers();
    if (serverIndex >= servers.size()) {
      throw new IllegalArgumentException("No such server #" + serverIndex + " (there are: " + servers.size() + " in stripe " + stripeIndex + ")");
    }
    return servers.get(serverIndex);
  }

  @Override
  public TerracottaServer getServer(UUID serverId) {
    for (Stripe stripe : stripes) {
      for (TerracottaServer terracottaServer : stripe.getServers()) {
        if (terracottaServer.getId().equals(serverId)) {
          return terracottaServer;
        }
      }
    }
    return null;
  }

  @Override
  public List<TerracottaServer> getServers() {
    List<TerracottaServer> servers = new ArrayList<>();
    for (Stripe stripe : stripes) {
      servers.addAll(stripe.getServers());
    }
    return servers;
  }

  @Override
  public Collection<String> getServersHostnames() {
    List<String> hostnames = new ArrayList<>();
    for (Stripe stripe : stripes) {
      for (TerracottaServer terracottaServer : stripe.getServers()) {
        hostnames.add(terracottaServer.getHostname());
      }
    }
    return hostnames;
  }

  @Override
  public void createDisruptionLinks(TerracottaServer terracottaServer,
                                    DisruptionProvider disruptionProvider,
                                    Map<ServerSymbolicName, Disruptor> disruptionLinks,
                                    Map<ServerSymbolicName, Integer> proxiedPorts) {
    PortChooser portChooser = new PortChooser();
    int stripeIndex = getStripeIndexOf(terracottaServer.getId());
    List<TerracottaServer> allServersInStripe = stripes.get(stripeIndex).getServers();
    for (TerracottaServer server : allServersInStripe) {
      if (!server.getServerSymbolicName().equals(terracottaServer.getServerSymbolicName())) {
        int tsaRandomGroupPort = portChooser.chooseRandomPort();
        final InetSocketAddress src = new InetSocketAddress(terracottaServer.getHostname(), tsaRandomGroupPort);
        final InetSocketAddress dest = new InetSocketAddress(server.getHostname(), server.getTsaGroupPort());
        disruptionLinks.put(server.getServerSymbolicName(), disruptionProvider.createLink(src, dest));
        proxiedPorts.put(server.getServerSymbolicName(), src.getPort());
      }
    }
  }
}
