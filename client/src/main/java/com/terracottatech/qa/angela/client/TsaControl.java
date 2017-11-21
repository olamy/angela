package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
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

import javax.cache.Cache;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;

/**
 * @author Aurelien Broszniowski
 */

public class TsaControl implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(TsaControl.class);
  public static final long TIMEOUT = 60000;

  private final Topology topology;
  private final IgniteCache<ServerSymbolicName, TerracottaServerState> terracottaServerStates;
  private final Ignite ignite;
  private final License license;
  private boolean closed = false;

  public TsaControl(final Topology topology) {
    this(topology, null);
  }

  public TsaControl(final Topology topology, final License license) {
    if (!topology.getLicenseType().isOpenSource() && license == null) {
      throw new IllegalArgumentException("LicenseType " + topology.getLicenseType() + " requires a license.");
    }
    this.topology = topology;
    this.license = license;


    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(this.topology.getServersHostnames());
    spi.setIpFinder(ipFinder);

    IgniteConfiguration cfg = new IgniteConfiguration();
    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    cfg.setIgniteInstanceName("Client@" + UUID.randomUUID().toString());

    ignite = Ignition.start(cfg);
    this.terracottaServerStates = ignite.getOrCreateCache("agentStates");
  }

  public void installAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);

        install(tcConfigIndex, terracottaServer);
      }
    }
  }

  private void install(int tcConfigIndex, TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = terracottaServerStates.get(terracottaServer.getServerSymbolicName());
    if (terracottaServerState != null) {
      throw new IllegalStateException("Cannot install: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    final int finalTcConfigIndex = tcConfigIndex;
    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));  //TODO :get offline flag

    logger.info("installing on {}", terracottaServer.getHostname());
    executeRemotely(terracottaServer.getHostname(), (IgniteRunnable)() ->
        Agent.CONTROL.install(topology, offline, license, finalTcConfigIndex));

    terracottaServerStates.put(terracottaServer.getServerSymbolicName(), TerracottaServerState.STOPPED);
  }

  public void uninstallAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);

        uninstall(terracottaServer);
      }
    }
  }

  private void uninstall(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = terracottaServerStates.get(terracottaServer.getServerSymbolicName());
    if (terracottaServerState == null) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STOPPED) {
      throw new IllegalStateException("Cannot uninstall: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    logger.info("uninstalling from {}", terracottaServer.getHostname());
    executeRemotely(terracottaServer.getHostname(), (IgniteRunnable)() ->
        Agent.CONTROL.uninstall(topology));

    terracottaServerStates.remove(terracottaServer.getServerSymbolicName());
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

  private void start(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = terracottaServerStates.get(terracottaServer.getServerSymbolicName());
    if (terracottaServerState == STARTED_AS_ACTIVE || terracottaServerState == STARTED_AS_PASSIVE) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STOPPED) {
      throw new IllegalStateException("Cannot stop: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    logger.info("starting on {}", terracottaServer.getHostname());
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

  private void stop(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = terracottaServerStates.get(terracottaServer.getServerSymbolicName());
    if (terracottaServerState == STOPPED) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STARTED_AS_ACTIVE && terracottaServerState != TerracottaServerState.STARTED_AS_PASSIVE) {
      throw new IllegalStateException("Cannot stop: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    logger.info("stopping on {}", terracottaServer.getHostname());
    TerracottaServerState state = executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteCallable<TerracottaServerState>)() ->
            Agent.CONTROL.stop(topology.getId(), terracottaServer));

    terracottaServerStates.put(terracottaServer.getServerSymbolicName(), state);
  }

  public void licenseAll() {
    Set<ServerSymbolicName> notStartedServers = new HashSet<>();
    for (Cache.Entry<ServerSymbolicName, TerracottaServerState> entry : terracottaServerStates) {
      if ((entry.getValue() != STARTED_AS_ACTIVE) && (entry.getValue() != STARTED_AS_PASSIVE)) {
        notStartedServers.add(entry.getKey());
      }
    }
    if (!notStartedServers.isEmpty()) {
      throw new IllegalStateException("The following Terracotta servers are not started : " + notStartedServers);
    }

    TcConfig[] tcConfigs = topology.getTcConfigs();
    TerracottaServer terracottaServer = tcConfigs[0].getServers().values().iterator().next();
    logger.info("Licensing all");
    executeRemotely(terracottaServer.getHostname(), (IgniteRunnable) () -> Agent.CONTROL.configureLicense(topology.getId(), terracottaServer, license, tcConfigs));
  }

  public ClientControl clientControl(String nodeName) {
    return new ClientControl(nodeName, ignite);
  }


  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    try {
      stopAll();
    } catch (IllegalStateException ise) {
      // ignore, not installed
    }
    uninstallAll();
    ignite.close();
  }


  private void executeRemotely(final String hostname,  final IgniteRunnable runnable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).broadcast(runnable);
  }

  private void executeRemotely(final String hostname, final long timeout, final IgniteRunnable runnable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).withTimeout(timeout).broadcast(runnable);
  }

  private <R> R executeRemotely(final String hostname, final long timeout, final IgniteCallable<R> callable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    Collection<R> results = ignite.compute(location).withTimeout(timeout).broadcast(callable);
    if (results.size() != 1) {
      throw new IllegalStateException("Multiple response for the Execution on " + hostname);
    }
    return results.iterator().next();
  }

}

