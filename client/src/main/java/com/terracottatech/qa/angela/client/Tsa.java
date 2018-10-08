package com.terracottatech.qa.angela.client;

import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.net.DisruptionController;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

  private final Ignite ignite;
  private final InstanceId instanceId;
  private final transient DisruptionController disruptionController;
  private final TsaConfigurationContext tsaConfigurationContext;
  private final LocalKitManager localKitManager;
  private boolean closed = false;

  Tsa(Ignite ignite, InstanceId instanceId, TsaConfigurationContext tsaConfigurationContext) {
    if (!tsaConfigurationContext.getTopology().getLicenseType().isOpenSource() && tsaConfigurationContext.getLicense() == null) {
      throw new IllegalArgumentException("LicenseType " + tsaConfigurationContext.getTopology().getLicenseType() + " requires a license.");
    }
    this.tsaConfigurationContext = tsaConfigurationContext;
    this.instanceId = instanceId;
    this.ignite = ignite;
    this.disruptionController = new DisruptionController(ignite, instanceId, tsaConfigurationContext.getTopology());
    this.localKitManager = new LocalKitManager(tsaConfigurationContext.getTopology().getDistribution());
    installAll();
  }

  public ClusterTool clusterTool(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      throw new IllegalStateException("Cannot control cluster tool: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return new ClusterTool(ignite, instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment());
  }

  public String licensePath(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      throw new IllegalStateException("Cannot get license path: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.getLicensePath(instanceId, terracottaServer)).get();
  }

  private void installAll() {
    Topology topology = tsaConfigurationContext.getTopology();
    for (TcConfig tcConfig : topology.getTcConfigs()) {
      for (TerracottaServer terracottaServer : tcConfig.getServers()) {
        if (tcConfig instanceof SecureTcConfig) {
          SecureTcConfig secureTcConfig = (SecureTcConfig) tcConfig;
          install(terracottaServer, secureTcConfig.securityRootDirectoryFor(terracottaServer.getServerSymbolicName()));
        } else {
          install(terracottaServer, null);
        }
      }
    }
  }

  private void install(TerracottaServer terracottaServer, SecurityRootDirectory securityRootDirectory) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState != TerracottaServerState.NOT_INSTALLED) {
      throw new IllegalStateException("Cannot install: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }

    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));
    Topology topology = tsaConfigurationContext.getTopology();
    License license = tsaConfigurationContext.getLicense();

    logger.info("Setting up locally the extracted install to be deployed remotely");
    String kitInstallationPath = System.getProperty("kitInstallationPath");
    localKitManager.setupLocalInstall(license, kitInstallationPath, offline);

    logger.info("Attempting to remotely installing if existing install already exists on {}", terracottaServer.getHostname());
    boolean isRemoteInstallationSuccessful;
    if (kitInstallationPath == null) {
      isRemoteInstallationSuccessful = executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.install(
          instanceId, topology, terracottaServer, offline, license, securityRootDirectory, localKitManager.getKitInstallationName(), topology.getDistribution()))
          .get();
    } else {
      isRemoteInstallationSuccessful = false;
    }
    if (!isRemoteInstallationSuccessful) {
      try {
        uploadKit(ignite, terracottaServer.getHostname(), instanceId, topology.getDistribution(),
            localKitManager.getKitInstallationName(), localKitManager.getKitInstallationPath());

        executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.install(instanceId, topology, terracottaServer, offline, license, securityRootDirectory,
            localKitManager.getKitInstallationName(), topology.getDistribution())).get();
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + terracottaServer.getHostname(), e);
      }
    }
  }

  private void uninstallAll() {
    Topology topology = tsaConfigurationContext.getTopology();
    for (TcConfig tcConfig : topology.getTcConfigs()) {
      for (TerracottaServer terracottaServer : tcConfig.getServers()) {
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
    executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.uninstall(instanceId, tsaConfigurationContext.getTopology(), terracottaServer, localKitManager
        .getKitInstallationName())).get();
  }

  public Tsa createAll() {
    tsaConfigurationContext.getTopology().getTcConfigs().stream()
        .flatMap(tcConfig -> tcConfig.getServers().stream())
        .map(server -> CompletableFuture.runAsync(() -> create(server)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
    return this;
  }

  public Tsa create(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    switch (terracottaServerState) {
      case STARTING:
      case STARTED_AS_ACTIVE:
      case STARTED_AS_PASSIVE:
        return this;
      case STOPPED:
        logger.info("creating on {}", terracottaServer.getHostname());
        executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.create(instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment())).get(TIMEOUT);
        return this;
    }
    throw new IllegalStateException("Cannot create: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
  }

  public Tsa startAll() {
    tsaConfigurationContext.getTopology().getTcConfigs().stream()
        .flatMap(tcConfig -> tcConfig.getServers().stream())
        .map(server -> CompletableFuture.runAsync(() -> start(server)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
    return this;
  }

  public DisruptionController disruptionController() {
    return disruptionController;
  }


  public Tsa start(final TerracottaServer terracottaServer) {
    create(terracottaServer);
    executeRemotely(ignite, terracottaServer,
        () -> Agent.CONTROLLER.waitForState(instanceId, terracottaServer, of(STARTED_AS_ACTIVE, STARTED_AS_PASSIVE))).get(TIMEOUT);
    return this;
  }

  public Tsa stopAll() {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    for (TcConfig tcConfig : topology.getTcConfigs()) {
      for (TerracottaServer terracottaServer : tcConfig.getServers()) {
        try {
          stop(terracottaServer);
        } catch (Exception e) {
          exceptions.add(e);
        }
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error stopping all servers");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public Tsa stop(final TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == STOPPED) {
      return this;
    }
    logger.info("stopping on {}", terracottaServer.getHostname());
    executeRemotely(ignite, terracottaServer, () -> Agent.CONTROLLER.stop(instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment())).get(TIMEOUT);
    return this;
  }

  public Tsa licenseAll() {
    licenseAll(null, null, false);
    return this;
  }

  public Tsa licenseAll(String clusterName) {
    licenseAll(clusterName, null, false);
    return this;
  }

  public Tsa licenseAll(SecurityRootDirectory securityRootDirectory) {
    licenseAll(null, securityRootDirectory, false);
    return this;
  }

  public Tsa licenseAll(String clusterName, SecurityRootDirectory securityRootDirectory, boolean verbose) {
    Topology topology = tsaConfigurationContext.getTopology();
    Set<ServerSymbolicName> notStartedServers = new HashSet<>();
    for (TerracottaServer terracottaServer : topology.getServers()) {
      TerracottaServerState terracottaServerState = getState(terracottaServer);
      if ((terracottaServerState != STARTED_AS_ACTIVE) && (terracottaServerState != STARTED_AS_PASSIVE)) {
        notStartedServers.add(terracottaServer.getServerSymbolicName());
      }
    }
    if (!notStartedServers.isEmpty()) {
      throw new IllegalStateException("The following Terracotta servers are not started : " + notStartedServers);
    }

    List<TcConfig> tcConfigs = topology.isNetDisruptionEnabled() ?
        disruptionController().updateTsaPortsWithProxy(topology.getTcConfigs()) :
        topology.getTcConfigs();

    TerracottaServer terracottaServer = tcConfigs.get(0).getServers().get(0);
    logger.info("Licensing all");
    executeRemotely(ignite, terracottaServer,
        () -> Agent.CONTROLLER.configureLicense(instanceId, terracottaServer, tcConfigs, clusterName, securityRootDirectory, tsaConfigurationContext.getTerracottaCommandLineEnvironment(), verbose)).get();
    return this;
  }

  public TerracottaServer findServer(int stripeId, int serverId) {
    return tsaConfigurationContext.getTopology().findServer(stripeId, serverId);
  }

  public TerracottaServerState getState(TerracottaServer terracottaServer) {
    return executeRemotely(ignite, terracottaServer,
        () -> Agent.CONTROLLER.getTerracottaServerState(instanceId, terracottaServer)).get();
  }

  public Collection<TerracottaServer> getPassives() {
    Collection<TerracottaServer> result = new ArrayList<>();
    for (TerracottaServer terracottaServer : tsaConfigurationContext.getTopology().getServers()) {
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
    for (TerracottaServer terracottaServer : tsaConfigurationContext.getTopology().getServers()) {
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
    if (disruptionController == null) {
      throw new IllegalStateException("uri cannot be built from a client lambda - please call uri() from the test code instead");
    }
    Topology topology = tsaConfigurationContext.getTopology();
    String prefix;
    if (topology.getDistribution().getVersion().getMajor() == 10) {
      prefix = "terracotta://";
    } else if (topology.getDistribution().getVersion().getMajor() == 4) {
      prefix = "";
    } else {
      throw new UnsupportedOperationException("Version " + topology.getDistribution()
          .getVersion() + " is not supported");
    }
    final Map<ServerSymbolicName, Integer> proxyTsaPorts = topology.isNetDisruptionEnabled() ? disruptionController.getProxyTsaPorts() : Collections
        .emptyMap();
    return URI.create(topology.getServers()
        .stream()
        .map(s -> s.getHostname() + ":" + proxyTsaPorts.getOrDefault(s.getServerSymbolicName(), s.getPorts().getTsaPort()))
        .collect(Collectors.joining(",", prefix, "")));
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
      logger.error("Error when trying to stop servers : {}", e.getMessage());
      // ignore, not installed
    }
    if (!ClusterFactory.SKIP_UNINSTALL) {
      uninstallAll();
    }

    if (tsaConfigurationContext.getTopology().isNetDisruptionEnabled()) {
      try {
        disruptionController.close();
      } catch (Exception e) {
        logger.error("Error when trying to close traffic controller : {}", e.getMessage());
      }
    }
  }

}
