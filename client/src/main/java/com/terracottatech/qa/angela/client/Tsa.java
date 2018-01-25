package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;

/**
 * @author Aurelien Broszniowski
 */

public class Tsa implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Tsa.class);
  private static final long TIMEOUT = 60000;

  private final Topology topology;
  private final Ignite ignite;
  private final License license;
  private final InstanceId instanceId;
  private boolean closed = false;

  Tsa(Ignite ignite, InstanceId instanceId, Topology topology) {
    this(ignite, instanceId, topology, null);
  }

  Tsa(Ignite ignite, InstanceId instanceId, Topology topology, License license) {
    if (!topology.getLicenseType().isOpenSource() && license == null) {
      throw new IllegalArgumentException("LicenseType " + topology.getLicenseType() + " requires a license.");
    }
    this.topology = topology;
    this.license = license;
    this.instanceId = instanceId;
    this.ignite = ignite;
  }

  public void installAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (int tcConfigIndex = 0; tcConfigIndex < tcConfigs.length; tcConfigIndex++) {
      final TcConfig tcConfig = tcConfigs[tcConfigIndex];
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);
        if (tcConfig instanceof SecureTcConfig) {
          SecureTcConfig secureTcConfig = (SecureTcConfig)tcConfig;
          install(tcConfigIndex, terracottaServer, secureTcConfig.securityRootDirectoryFor(serverSymbolicName));
        } else {
          install(tcConfigIndex, terracottaServer, null);
        }
      }
    }
  }

  private void install(int tcConfigIndex, TerracottaServer terracottaServer, SecurityRootDirectory securityRootDirectory) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState != TerracottaServerState.NOT_INSTALLED) {
      throw new IllegalStateException("Cannot install: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    final int finalTcConfigIndex = tcConfigIndex;
    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));  //TODO :get offline flag

    logger.info("installing on {}", terracottaServer.getHostname());
    executeRemotely(terracottaServer.getHostname(), (IgniteRunnable)() ->
        Agent.CONTROLLER.install(instanceId, topology, terracottaServer, offline, license, finalTcConfigIndex,
          securityRootDirectory));
  }

  public void uninstallAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (final TcConfig tcConfig : tcConfigs) {
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);

        uninstall(terracottaServer);
      }
    }
  }

  private void uninstall(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STOPPED) {
      throw new IllegalStateException("Cannot uninstall: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    logger.info("uninstalling from {}", terracottaServer.getHostname());
    executeRemotely(terracottaServer.getHostname(), (IgniteRunnable)() ->
        Agent.CONTROLLER.uninstall(instanceId, topology, terracottaServer));
  }

  public void startAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (final TcConfig tcConfig : tcConfigs) {
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);
        start(terracottaServer);
      }
    }
  }

  public void start(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == STARTED_AS_ACTIVE || terracottaServerState == STARTED_AS_PASSIVE) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STOPPED) {
      throw new IllegalStateException("Cannot stop: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    logger.info("starting on {}", terracottaServer.getHostname());
    executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteRunnable)() -> Agent.CONTROLLER.start(instanceId, terracottaServer));
  }

  public void stopAll() {
    TcConfig[] tcConfigs = topology.getTcConfigs();
    for (final TcConfig tcConfig : tcConfigs) {
      for (ServerSymbolicName serverSymbolicName : tcConfig.getServers().keySet()) {
        TerracottaServer terracottaServer = topology.getServers().get(serverSymbolicName);
        stop(terracottaServer);
      }
    }
  }

  public void stop(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == STOPPED) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STARTED_AS_ACTIVE && terracottaServerState != TerracottaServerState.STARTED_AS_PASSIVE) {
      throw new IllegalStateException("Cannot stop: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    logger.info("stopping on {}", terracottaServer.getHostname());
    executeRemotely(terracottaServer.getHostname(), TIMEOUT,
        (IgniteRunnable)() -> Agent.CONTROLLER.stop(instanceId, terracottaServer));
  }

  public void licenseAll() {
    licenseAll(null);
  }

  public void licenseAll(final SecurityRootDirectory securityRootDirectory) {
    Set<ServerSymbolicName> notStartedServers = new HashSet<>();
    for (Map.Entry<ServerSymbolicName, TerracottaServer> entry : topology.getServers().entrySet()) {
      TerracottaServerState terracottaServerState = getState(entry.getValue());
      if ((terracottaServerState != STARTED_AS_ACTIVE) && (terracottaServerState != STARTED_AS_PASSIVE)) {
        notStartedServers.add(entry.getKey());
      }
    }
    if (!notStartedServers.isEmpty()) {
      throw new IllegalStateException("The following Terracotta servers are not started : " + notStartedServers);
    }

    TcConfig[] tcConfigs = topology.getTcConfigs();
    TerracottaServer terracottaServer = tcConfigs[0].getServers().values().iterator().next();
    logger.info("Licensing all");
    executeRemotely(terracottaServer.getHostname(), (IgniteRunnable) () -> Agent.CONTROLLER.configureLicense(instanceId, terracottaServer, license, tcConfigs,
      securityRootDirectory));
  }

  public TerracottaServerState getState(TerracottaServer terracottaServer) {
    return executeRemotely(terracottaServer.getHostname(), () ->
        Agent.CONTROLLER.getTerracottaServerState(instanceId, terracottaServer));
  }

  public Collection<TerracottaServer> getPassives() {
    Collection<TerracottaServer> result = new ArrayList<>();
    for (TerracottaServer terracottaServer : topology.getServers().values()) {
      if (getState(terracottaServer) == STARTED_AS_PASSIVE) {
        result.add(terracottaServer);
      }
    }
    return result;
  }

  public TerracottaServer getPassive() {
    Collection<TerracottaServer> servers = getPassives();
    switch (servers.size()) {
      case 0:
        return null;
      case 1:
        return servers.iterator().next();
      default:
        throw new IllegalStateException("There is more than one Passive Terracotta server, found " + servers.size());
    }
  }

  public Collection<TerracottaServer> getActives() {
    Collection<TerracottaServer> result = new ArrayList<>();
    for (TerracottaServer terracottaServer : topology.getServers().values()) {
      if (getState(terracottaServer) == STARTED_AS_ACTIVE) {
        result.add(terracottaServer);
      }
    }
    return result;
  }

  public TerracottaServer getActive() {
    Collection<TerracottaServer> servers = getActives();
    switch (servers.size()) {
      case 0:
        return null;
      case 1:
        return servers.iterator().next();
      default:
        throw new IllegalStateException("There is more than one Active Terracotta server, found " + servers.size());
    }
  }

  public URI uri() {
    StringBuilder sb = new StringBuilder("terracotta://");
    for (TerracottaServer terracottaServer : topology.getServers().values()) {
      sb.append(terracottaServer.getHostname()).append(":").append(terracottaServer.getPorts().getTsaPort());
      sb.append(",");
    }
    if (!topology.getServers().isEmpty()) {
      sb.deleteCharAt(sb.length() - 1);
    }
    try {
      return new URI(sb.toString());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    try {
      stopAll();
    } catch (Exception e) {
      // ignore, not installed
    }
    uninstallAll();
  }


  private void executeRemotely(final String hostname, final IgniteRunnable runnable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).broadcast(runnable);
  }

  private void executeRemotely(final String hostname, final long timeout, final IgniteRunnable runnable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).withTimeout(timeout).broadcast(runnable);
  }

  private <R> R executeRemotely(final String hostname, final IgniteCallable<R> callable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    Collection<R> results = ignite.compute(location).broadcast(callable);
    if (results.size() != 1) {
      throw new IllegalStateException("Multiple response for the Execution on " + hostname);
    }
    return results.iterator().next();
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

