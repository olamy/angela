package com.terracottatech.qa.angela.client;

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

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;

/**
 * @author Aurelien Broszniowski
 */

public class TsaControl {

  private final static Logger logger = LoggerFactory.getLogger(TsaControl.class);

  private final Topology topology;
  private final Map<ServerSymbolicName, TerracottaServerState> terracottaServerStates;

  public static final long TIMEOUT = 60000;

  private volatile Ignite ignite;
  private final License license;

  public TsaControl(final Topology topology) {
    this(topology, null);
  }

  public TsaControl(final Topology topology, final License license) {
    if (!topology.getLicenseType().isOpenSource() && license == null) {
      throw new IllegalArgumentException("LicenseType " + topology.getLicenseType() + " requires a license.");
    }
    this.topology = topology;
    this.terracottaServerStates = createTerracottaServerStatesMap(this.topology.getTcConfigs());
    this.license = license;
  }

  public void install() {
    if (ignite != null) {
      throw new IllegalStateException("You can not install TsaControl twice");
    }

    IgniteConfiguration cfg = new IgniteConfiguration();

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(topology.getServersHostnames());
    spi.setIpFinder(ipFinder);

    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    cfg.setIgniteInstanceName("Client@" + UUID.randomUUID().toString());

    ignite = Ignition.start(cfg);

    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);

        final int finalTcConfigIndex = tcConfigIndex;
        boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));  //TODO :get offline flag

        executeRemotely(terracottaServer.getHostname(), (IgniteRunnable)() ->
            Agent.CONTROL.init(topology, offline, license, finalTcConfigIndex));
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


  private static Map<ServerSymbolicName, TerracottaServerState> createTerracottaServerStatesMap(final TcConfig[] tcConfigs) {
    Map<ServerSymbolicName, TerracottaServerState> terracottaServerStates = new HashMap<>();
    for (TcConfig tcConfig : tcConfigs) {
      for (TerracottaServer terracottaServer : tcConfig.getServers().values()) {
        terracottaServerStates.put(terracottaServer.getServerSymbolicName(), TerracottaServerState.STOPPED);
      }
    }
    return terracottaServerStates;
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
      throw new IllegalStateException("TsaControl missing calls to withTopology() and install().");
    }

    if (terracottaServerState == STARTED_AS_ACTIVE || terracottaServerState == STARTED_AS_PASSIVE) {
      return;
    }

    TerracottaServerState state = executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteCallable<TerracottaServerState>)() ->
            Agent.CONTROL.start(topology.getId(), terracottaServer));

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
      throw new IllegalStateException("TsaControl missing calls to withTopology() and install().");
    }

    if (terracottaServerState == STOPPED) {
      return;
    }

    TerracottaServerState state = executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteCallable<TerracottaServerState>)() ->
            Agent.CONTROL.stop(topology.getId(), terracottaServer));

    terracottaServerStates.put(terracottaServer.getServerSymbolicName(), state);
  }

  public void licenseAll() {
    Set<ServerSymbolicName> notStartedServers = new HashSet<>();
    for (Map.Entry<ServerSymbolicName, TerracottaServerState> entry : terracottaServerStates.entrySet()) {
      if ((entry.getValue() != STARTED_AS_ACTIVE) && (entry.getValue() != STARTED_AS_PASSIVE)) {
        notStartedServers.add(entry.getKey());
      }
    }
    if (!notStartedServers.isEmpty()) {
      throw new IllegalStateException("The following Terracotta servers are not started : " + notStartedServers);
    }

    TcConfig[] tcConfigs = topology.getTcConfigs();
    TerracottaServer terracottaServer = tcConfigs[0].getServers().values().iterator().next();
    executeRemotely(terracottaServer.getHostname(), new IgniteRunnable() {
      @Override
      public void run() {
        Agent.CONTROL.configureLicense(topology.getId(), terracottaServer, license, tcConfigs);
      }
    });
  }
}

