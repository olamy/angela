package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.tcconfig.ServerSymbolicName;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.kit.TerracottaServerState;
import com.terracottatech.qa.angela.tcconfig.ClusterToolConfig;
import com.terracottatech.qa.angela.tcconfig.TcConfig;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.topology.Topology;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.terracottatech.qa.angela.kit.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.kit.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.kit.TerracottaServerState.STOPPED;

/**
 * @author Aurelien Broszniowski
 */

public class TsaControl {

  private final static Logger logger = LoggerFactory.getLogger(TsaControl.class);

  private Topology topology;
  private Map<ServerSymbolicName, TerracottaServerState> terracottaServerStates;

  private ClusterToolConfig clusterToolConfig;

  public static final long TIMEOUT = 60000;

  private volatile Ignite ignite;

  public void init() {
    if (topology == null) {
      throw new IllegalArgumentException("You need to pass a topology");
    }

    if (ignite != null) {
      throw new IllegalStateException("You can not init TsaControl twice");
    }

    IgniteConfiguration cfg = new IgniteConfiguration();

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(topology.getServersHostnames());
    spi.setIpFinder(ipFinder);

    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    cfg.setIgniteInstanceName(UUID.randomUUID().toString());
    cfg.setPeerClassLoadingEnabled(true);

    ignite = Ignition.start(cfg);

    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);

        final int finalTcConfigIndex = tcConfigIndex;
        boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));  //TODO :get offline flag

        executeRemotely(terracottaServer.getHostname(), (IgniteRunnable)() ->
            AgentControl.agentControl.init(topology, offline, clusterToolConfig, finalTcConfigIndex));
      }
    }
  }

  private void executeRemotely(final String hostname,  final IgniteRunnable runnable) {
    logger.info("Executing command on {}", hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).broadcast(runnable);
  }

  private void executeRemotely(final String hostname, final long timeout, final IgniteRunnable runnable) {
    logger.info("Executing command on {}", hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).withTimeout(timeout).broadcast(runnable);
  }

  private <R> R executeRemotely(final String hostname, final long timeout, final IgniteCallable<R> callable) {
    logger.info("Executing command on {}", hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    Collection<R> results = ignite.compute(location).withTimeout(timeout).broadcast(callable);
    if (results.size() != 1) {
      throw new IllegalStateException("Multiple response for the Execution on " + hostname);
    }
    return results.iterator().next();
  }

  public void close() {
    IgniteCache<String, String> kitsInstalls = ignite.getOrCreateCache("installs");
    kitsInstalls.remove(topology.getId());

    ignite.close();
  }


  public TsaControl withTopology(Topology topology) {
    this.topology = topology;
    createTerracottaServerStatesMap(this.topology.getTcConfigs());
    return this;
  }

  private void createTerracottaServerStatesMap(final TcConfig[] tcConfigs) {
    terracottaServerStates = new HashMap<>();
    for (TcConfig tcConfig : tcConfigs) {
      for (TerracottaServer terracottaServer : tcConfig.getServers().values()) {
        terracottaServerStates.put(terracottaServer.getServerSymbolicName(), TerracottaServerState.STOPPED);
      }
    }
  }

  public TsaControl withClusterToolConfig(final ClusterToolConfig clusterToolConfig) {
    this.clusterToolConfig = clusterToolConfig;
    return this;
  }

  public void startAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);
        start(terracottaServer);
      }
    }
  }

  public void start(final TerracottaServer terracottaServer) {
    startServer(terracottaServer);
  }

  private void startServer(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = terracottaServerStates.get(terracottaServer.getServerSymbolicName());
    if (terracottaServerState == null) {
      throw new IllegalStateException("TsaControl missing calls to withTopology() and init().");
    }

    if (terracottaServerState == STARTED_AS_ACTIVE || terracottaServerState == STARTED_AS_PASSIVE) {
      return;
    }

    TerracottaServerState state = executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteCallable<TerracottaServerState>)() ->
            AgentControl.agentControl.start(topology.getId(), terracottaServer));

    terracottaServerStates.put(terracottaServer.getServerSymbolicName(), state);
  }

  public void stopAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);
        stop(terracottaServer);
      }
    }
  }

  public void stop(final TerracottaServer terracottaServer) {
    stopServer(terracottaServer);
  }

  private void stopServer(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = terracottaServerStates.get(terracottaServer.getServerSymbolicName());
    if (terracottaServerState == null) {
      throw new IllegalStateException("TsaControl missing calls to withTopology() and init().");
    }

    if (terracottaServerState == STOPPED) {
      return;
    }

    TerracottaServerState state = executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteCallable<TerracottaServerState>)() ->
            AgentControl.agentControl.stop(topology.getId(), terracottaServer));

    terracottaServerStates.put(terracottaServer.getServerSymbolicName(), state);
  }
}

