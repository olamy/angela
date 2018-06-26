package com.terracottatech.qa.angela.client.net;

import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.DisruptionProviderFactory;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 */
public class DisruptionController implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DisruptionController.class);
  private static final DisruptionProvider DISRUPTION_PROVIDER = DisruptionProviderFactory.getDefault();
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final Topology topology;
  private final Collection<Disruptor> existingDisruptors = new ArrayList<>();
  private volatile boolean closed;
  private Map<ServerSymbolicName, Integer> proxyTsaPorts = new HashMap<>();


  private static final Map<InstanceId, DisruptionController> controllers = new ConcurrentHashMap<>();

  public static void add(Ignite ignite, InstanceId instanceId, Topology topology) {
    controllers.putIfAbsent(instanceId, new DisruptionController(ignite, instanceId, topology));
  }

  public static DisruptionController get(InstanceId instanceId) {
    return controllers.get(instanceId);
  }

  public static void remove(InstanceId instanceId) {
    controllers.remove(instanceId);
  }

  private DisruptionController(Ignite ignite, InstanceId instanceId, Topology topology) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.topology = topology;
  }

  /**
   * Create disruptor to control traffic between all servers specified.
   * (ex: Server1 <-> Server2, Server2 <-> Server3 & Server3 <-> Server1)
   *
   * @param servers
   * @return
   */
  public ServerToServerDisruptor newServerToServerDisruptor(TerracottaServer... servers) {
    if (servers.length < 2) {
      throw new IllegalArgumentException("Two or more split clusters required for server to server disruption");
    }
    SplitCluster[] splitClusters = new SplitCluster[servers.length];
    for (int i = 0; i < servers.length; ++i) {
      splitClusters[i] = new SplitCluster(servers[i]);
    }
    return newServerToServerDisruptor(splitClusters);
  }


  /**
   * Create disruptor to control traffic between set of servers specified
   * (ex: [Server1] <-> [Server2,Server3]. No disruption between Server2 and Server3 in this example)
   *
   * @param splitClusters
   * @return
   */
  public ServerToServerDisruptor newServerToServerDisruptor(SplitCluster... splitClusters) {

    if (!topology.isNetDisruptionEnabled()) {
      throw new IllegalArgumentException("Topology not enabled for network disruption");
    }
    if (closed) {
      throw new IllegalStateException("already closed");
    }
    if (splitClusters.length < 2) {
      throw new IllegalArgumentException("Two or more split clusters required for server to server disruption");
    }

    //
    for (SplitCluster splitCluster : splitClusters) {
      if (splitCluster.getServers().isEmpty()) {
        throw new IllegalArgumentException("Empty split cluster " + splitCluster);
      }
    }

    //validate for any duplicate server
    for (int i = 0; i < splitClusters.length; ++i) {
      for (int j = i + 1; j < splitClusters.length; ++j) {
        SplitCluster cluster1 = splitClusters[i];
        SplitCluster cluster2 = splitClusters[j];
        if (!Collections.disjoint(cluster1.getServers(), cluster2.getServers())) {
          throw new IllegalArgumentException("Duplicate servers found in split clusters { " + cluster1 + " } and { " + cluster2 + " }");
        }
      }
    }

    LOGGER.debug("new disruptor for {}", (Object)splitClusters);
    //compute servers to be linked for disruption based on input split clusters
    final Map<ServerSymbolicName, Collection<ServerSymbolicName>> linkedServers = new HashMap<>();
    for (int i = 0; i < splitClusters.length; ++i) {
      for (int j = i + 1; j < splitClusters.length; ++j) {
        SplitCluster cluster1 = splitClusters[i];
        SplitCluster cluster2 = splitClusters[j];
        for (ServerSymbolicName server : cluster1.getServers()) {
          linkedServers.computeIfAbsent(server, key -> new ArrayList<>()).addAll(cluster2.getServers());
        }
        for (ServerSymbolicName server : cluster2.getServers()) {
          linkedServers.computeIfAbsent(server, key -> new ArrayList<>()).addAll(cluster1.getServers());
        }
      }
    }


    //validate if any server to be linked is already linked
    final Set<ServerSymbolicName> alreadyLinkedServers = new HashSet<>();
    for (Map.Entry<ServerSymbolicName, Collection<ServerSymbolicName>> entry : linkedServers.entrySet()) {
      ServerSymbolicName server = entry.getKey();
      Collection<ServerSymbolicName> newConnected = entry.getValue();
      for (Disruptor disruption : existingDisruptors) {
        if (disruption instanceof ServerToServerDisruptor) {
          ServerToServerDisruptor serverToServerDisruptor = (ServerToServerDisruptor)disruption;
          Collection<ServerSymbolicName> alreadyConnected = serverToServerDisruptor.getLinkedServers().get(server);
          if (alreadyConnected != null && !Collections.disjoint(alreadyConnected, newConnected)) {
            alreadyLinkedServers.add(server);
          }
        }

      }
    }
    if (alreadyLinkedServers.size() > 0) {
      throw new IllegalArgumentException("Servers are already linked:" + alreadyLinkedServers);
    }


    ServerToServerDisruptor disruption = new ServerToServerDisruptor(ignite, instanceId, topology, linkedServers, d -> removeDisruptor(d));
    existingDisruptors.add(disruption);
    LOGGER.debug("created disruptor {}", disruption);
    return disruption;
  }

  /**
   * Create client to server disruptor for controlling traffic between
   * client like DatasetManager & CacheManager and servers. This needs to
   * be created before initializing DatasetManager or CacheManager and use
   * connection URI obtained from this disruptor {@link ClientToServerDisruptor#uri()}
   *
   * @return
   */
  public ClientToServerDisruptor newClientToServerDisruptor() {
    if (!topology.isNetDisruptionEnabled()) {
      throw new IllegalArgumentException("Topology not enabled for network disruption");
    }
    LOGGER.debug("creating new client to servers disruption");
    Optional<Disruptor> disruptor = existingDisruptors.stream()
        .filter(d -> d instanceof ClientToServerDisruptor)
        .findAny();
    if (DISRUPTION_PROVIDER.isProxyBased() & disruptor.isPresent()) {
      //make sure single disruptor serves all clients
      return (ClientToServerDisruptor)disruptor.get();
    } else {
      ClientToServerDisruptor newDisruptor = new ClientToServerDisruptor(topology, d -> removeDisruptor(d), proxyTsaPorts);
      existingDisruptors.add(newDisruptor);
      return newDisruptor;
    }
  }


  /**
   * @param disruptor
   */
  void removeDisruptor(Disruptor disruptor) {
    LOGGER.debug("removing {}", disruptor);
    existingDisruptors.remove(disruptor);
  }


  @Override
  public void close() throws Exception {
    LOGGER.debug("closing disruption controller");
    ArrayList<Disruptor> copy = new ArrayList<>(existingDisruptors);
    for (Disruptor disruption : copy) {
      disruption.close();
    }
    closed = true;
  }


  public TcConfig[] updateTsaPortsWithProxy(TcConfig[] configs) {
    if (DISRUPTION_PROVIDER.isProxyBased()) {
      TcConfig[] proxiedTcConfigs = new TcConfig[configs.length];
      for (int i = 0; i < configs.length; ++i) {
        proxiedTcConfigs[i] = TcConfig.copy(configs[i]);
        proxyTsaPorts.putAll(proxiedTcConfigs[i].retrieveTsaPorts(true));
      }
      //create disruptor up front for cluster tool configuration
      ClientToServerDisruptor newDisruptor = new ClientToServerDisruptor(topology, d -> removeDisruptor(d), proxyTsaPorts);
      existingDisruptors.add(newDisruptor);
      return proxiedTcConfigs;
    } else {
      return configs;
    }
  }
}
