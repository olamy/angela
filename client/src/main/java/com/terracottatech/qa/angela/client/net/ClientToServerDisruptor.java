package com.terracottatech.qa.angela.client.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.DisruptionProviderFactory;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.net.DisruptorState;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Disruptor to control traffic between client and servers. This needs to
 * be created before initializing client(DatasetManager & CacheManager) and use
 * connection URI obtained from this disruptor {@link #uri()}
 * <p>
 * <p>
 * <p>
 * <p>
 * <p>
 * This disruptor doesn't work with TopoConnectionService since topology configuration overrides proxy url before making
 * actual connection. So this disruption requires a test connection service(similar to DelayedConnectionService found in galvan)
 * to be created to skip topolgoy configuration or delegate to LeasedConnectionServiceImpl.
 */
public class ClientToServerDisruptor implements Disruptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientToServerDisruptor.class);
  private static final DisruptionProvider DISRUPTION_PROVIDER = DisruptionProviderFactory.getDefault();

  private final Map<ServerSymbolicName, Disruptor> links = new HashMap<>();
  private final Map<ServerSymbolicName, InetSocketAddress> endPoints = new HashMap<>();
  private final Consumer<Disruptor> closeHook;
  private volatile DisruptorState state;

  ClientToServerDisruptor(Topology topology, Consumer<Disruptor> closeHook, Map<ServerSymbolicName, Integer> proxiedTsaPorts) {
    this.closeHook = closeHook;
    for (TerracottaServer server : topology.getServers().values()) {
      final InetSocketAddress clientEndPoint = DISRUPTION_PROVIDER.isProxyBased() ? null : new InetSocketAddress("localhost", -1);
      final InetSocketAddress proxyEndPoint = DISRUPTION_PROVIDER.isProxyBased() ? new InetSocketAddress("localhost", proxiedTsaPorts
          .get(server.getServerSymbolicName())) : null;
      final InetSocketAddress serverEndPoint = new InetSocketAddress(server.getHostname(), server.getPorts()
          .getTsaPort());

      endPoints.putIfAbsent(server.getServerSymbolicName(), DISRUPTION_PROVIDER.isProxyBased() ? proxyEndPoint : serverEndPoint);
      LOGGER.info("Server {} endpoint {}", server.getServerSymbolicName()
          .getSymbolicName(), endPoints.get(server.getServerSymbolicName()));
      links.computeIfAbsent(server.getServerSymbolicName(), s -> DISRUPTION_PROVIDER.createLink(DISRUPTION_PROVIDER.isProxyBased() ? proxyEndPoint : clientEndPoint, serverEndPoint));
    }
    state = DisruptorState.UNDISRUPTED;
  }


  public void disrupt(Collection<ServerSymbolicName> servers) {
    if (state != DisruptorState.UNDISRUPTED) {
      throw new IllegalStateException("Illegal state before disrupt:" + state);
    }

    LOGGER.info("blocking client to {}", servers.stream()
        .map(s -> s.getSymbolicName())
        .collect(Collectors.joining(",", "[", "]")));

    for (ServerSymbolicName server : servers) {
      links.get(server).disrupt();
    }
    state = DisruptorState.DISRUPTED;
  }

  @Override
  public void disrupt() {
    disrupt(links.keySet());
  }

  @Override
  public void undisrupt() {
    if (state == DisruptorState.DISRUPTED) {
      LOGGER.info("undisrupting client to servers");
      for (Disruptor link : links.values()) {
        link.undisrupt();
      }
      state = DisruptorState.UNDISRUPTED;
    } else {
      //ignore?
    }
  }

  @Override
  public void close() throws Exception {
    links.values().stream().forEach(DISRUPTION_PROVIDER::removeLink);
    closeHook.accept(this);
    state = DisruptorState.CLOSED;
  }

  public URI uri() {
    try {
      return URI.create("terracotta://" + getHostPortList());
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    }
  }

  public URI uri(TerracottaServer... servers) {
    return URI.create("terracotta://" + getHostPortList(servers));
  }

  public URI diagnosticURI(TerracottaServer server) {
    return URI.create("diagnostics://" + getHostPortList(server));
  }

  private String getHostPortList() {
    return getHostPortList(endPoints.keySet());
  }

  private String getHostPortList(TerracottaServer... servers) {
    return getHostPortList(Arrays.asList(servers)
        .stream()
        .map(TerracottaServer::getServerSymbolicName)
        .collect(Collectors.toList()));
  }

  private String getHostPortList(Collection<ServerSymbolicName> servers) {

    return servers.stream()
        .map(s -> endPoints.get(s))
        .map(s -> s.getHostName() + ":" + s.getPort())
        .collect(Collectors.joining(","));
  }


}
