package com.terracottatech.qa.angela.client;

import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.net.DisruptionController;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.terracottatech.qa.angela.client.IgniteHelper.executeRemotely;
import static com.terracottatech.qa.angela.client.IgniteHelper.uploadKit;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static java.util.EnumSet.of;

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
  private final TerracottaCommandLineEnvironment tcEnv;
  private boolean closed = false;
  private LocalKitManager localKitManager;

  Tsa(Ignite ignite, InstanceId instanceId, Topology topology, License license, TerracottaCommandLineEnvironment tcEnv) {
    this.tcEnv = tcEnv;
    if (!topology.getLicenseType().isOpenSource() && license == null) {
      throw new IllegalArgumentException("LicenseType " + topology.getLicenseType() + " requires a license.");
    }
    this.topology = topology;
    this.license = license;
    this.instanceId = instanceId;
    this.ignite = ignite;
    DisruptionController.add(ignite, instanceId, topology);
    this.localKitManager = new LocalKitManager(topology.getDistribution());
  }

  public ClusterTool clusterTool(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      throw new IllegalStateException("Cannot control cluster tool: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return new ClusterTool(ignite, instanceId, terracottaServer, tcEnv);
  }

  public String licensePath(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      throw new IllegalStateException("Cannot get license path: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.getLicensePath(instanceId)).get();
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

    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));

    logger.info("Setting up locally the extracted install to be deployed remotely");
    localKitManager.setupLocalInstall(license, offline);

    logger.info("Attempting to remotely installing if existing install already exists on {}", terracottaServer.getHostname());
    boolean isRemoteInstallationSuccessful = executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.attemptRemoteInstallation(
        instanceId, topology, terracottaServer, offline, license, tcConfigIndex, securityRootDirectory, localKitManager.getKitInstallationName()))
        .get();
    if (!isRemoteInstallationSuccessful) {
      try {
        uploadKit(ignite, terracottaServer.getHostname(), instanceId, topology.getDistribution(),
            localKitManager.getKitInstallationName(), localKitManager.getKitInstallationPath());

        executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.install(instanceId, topology, terracottaServer, offline, license, tcConfigIndex, securityRootDirectory,
            localKitManager.getKitInstallationName())).get();
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + terracottaServer.getHostname(), e);
      }
    }
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
    executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.uninstall(instanceId, topology, terracottaServer, localKitManager
        .getKitInstallationName())).get();
  }

  public void createAll() {
    Stream.of(topology.getTcConfigs())
        .flatMap(tcConfig -> tcConfig.getServers().values().stream())
        .map(server -> CompletableFuture.runAsync(() -> create(server)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
  }

  public void create(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    switch (terracottaServerState) {
      case STARTING:
      case STARTED_AS_ACTIVE:
      case STARTED_AS_PASSIVE:
        return;
      case STOPPED:
        logger.info("creating on {}", terracottaServer.getHostname());
        executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.create(instanceId, terracottaServer, tcEnv)).get(TIMEOUT);
        return;
    }
    throw new IllegalStateException("Cannot create: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
  }

  public void startAll() {
    Stream.of(topology.getTcConfigs())
        .flatMap(tcConfig -> tcConfig.getServers().values().stream())
        .map(server -> CompletableFuture.runAsync(() -> start(server)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
  }

  public DisruptionController disruptionController() {
    return DisruptionController.get(instanceId);
  }


  public void start(final TerracottaServer terracottaServer) {
    create(terracottaServer);
    executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.waitForState(instanceId, terracottaServer, of(STARTED_AS_ACTIVE, STARTED_AS_PASSIVE)))
        .get(TIMEOUT);
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
    logger.info("stopping on {}", terracottaServer.getHostname());
    executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.stop(instanceId, terracottaServer, tcEnv)).get(TIMEOUT);
  }

  public void licenseAll() {
    licenseAll(null, null);
  }

  public void licenseAll(String clusterName) {
    licenseAll(clusterName, null);
  }

  public void licenseAll(SecurityRootDirectory securityRootDirectory) {
    licenseAll(null, securityRootDirectory);
  }

  public void licenseAll(String clusterName, SecurityRootDirectory securityRootDirectory) {
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

    TcConfig[] tcConfigs = topology.isNetDisruptionEnabled() ? disruptionController().updateTsaPortsWithProxy(topology.getTcConfigs()) : topology
        .getTcConfigs();

    TerracottaServer terracottaServer = tcConfigs[0].getServers().values().iterator().next();
    logger.info("Licensing all");
    executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.configureLicense(instanceId, terracottaServer, tcConfigs, clusterName, securityRootDirectory, tcEnv))
        .get();
  }

  public TerracottaServerState getState(TerracottaServer terracottaServer) {
    return executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.getTerracottaServerState(instanceId, terracottaServer))
        .get();
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

  public Collection<TerracottaServer> getServers() {
    return topology.getServers().values();
  }

  public TerracottaServer getServer(ServerSymbolicName symbolicName) {
    return topology.getServers().get(symbolicName);
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

  public RemoteFolder browse(TerracottaServer terracottaServer, String root) {
    String path = executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.getInstallPath(instanceId, terracottaServer))
        .get();
    return new RemoteFolder(ignite, terracottaServer.getHostname(), path, root);
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
      logger.error("Error when trying to stop servers", e.getMessage());
      // ignore, not installed
    }
    if (!ClusterFactory.SKIP_UNINSTALL) {
      uninstallAll();
    }

    if (topology.isNetDisruptionEnabled()) {
      try {
        DisruptionController.get(instanceId).close();
        DisruptionController.remove(instanceId);
      } catch (Exception e) {
        logger.error("Error when trying to close traffic controller", e.getMessage());
      }
    }
  }

}

