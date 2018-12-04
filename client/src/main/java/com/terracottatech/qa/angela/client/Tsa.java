package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.distribution.Distribution;
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static java.util.EnumSet.of;

/**
 * @author Aurelien Broszniowski
 */

public class Tsa implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Tsa.class);

  private final Ignite ignite;
  private final InstanceId instanceId;
  private final transient DisruptionController disruptionController;
  private final TsaConfigurationContext tsaConfigurationContext;
  private final LocalKitManager localKitManager;
  private boolean closed = false;

  Tsa(Ignite ignite, InstanceId instanceId, TsaConfigurationContext tsaConfigurationContext) {
    this.tsaConfigurationContext = tsaConfigurationContext;
    this.instanceId = instanceId;
    this.ignite = ignite;
    this.disruptionController = new DisruptionController(ignite, instanceId, tsaConfigurationContext.getTopology());
    this.localKitManager = new LocalKitManager(tsaConfigurationContext.getTopology().getDistribution());
    installAll();
  }

  public TsaConfigurationContext getTsaConfigurationContext() {
    return tsaConfigurationContext;
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
    return IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.getTsaLicensePath(instanceId, terracottaServer));
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
    installWithKitManager(terracottaServer, securityRootDirectory, this.localKitManager);
  }

  private void installWithKitManager(TerracottaServer terracottaServer, SecurityRootDirectory securityRootDirectory, LocalKitManager localKitManager) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState != TerracottaServerState.NOT_INSTALLED) {
      throw new IllegalStateException("Cannot install: server " + terracottaServer.getServerSymbolicName() + " already in state " + terracottaServerState);
    }
    Distribution distribution = localKitManager.getDistribution();

    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));
    Topology topology = tsaConfigurationContext.getTopology();
    License license = tsaConfigurationContext.getLicense();

    String kitInstallationPath = System.getProperty("kitInstallationPath");
    localKitManager.setupLocalInstall(license, kitInstallationPath, offline);

    boolean isRemoteInstallationSuccessful;
    if (kitInstallationPath == null) {
      logger.info("Attempting to remotely install if distribution already exists on {}", terracottaServer.getHostname());
      isRemoteInstallationSuccessful = IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.installTsa(
          instanceId, topology, terracottaServer, offline, license, securityRootDirectory, localKitManager.getKitInstallationName(), distribution));
    } else {
      isRemoteInstallationSuccessful = false;
    }
    if (!isRemoteInstallationSuccessful) {
      try {
        logger.info("Uploading {} on {}", distribution, terracottaServer.getHostname());
        IgniteClientHelper.uploadKit(ignite, terracottaServer.getHostname(), instanceId, distribution,
            localKitManager.getKitInstallationName(), localKitManager.getKitInstallationPath());

        IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.installTsa(instanceId, topology, terracottaServer, offline, license, securityRootDirectory,
            localKitManager.getKitInstallationName(), distribution));
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + terracottaServer.getHostname(), e);
      }
    }
  }

  public Tsa upgrade(TerracottaServer server, Distribution newDistribution) {
    logger.info("Upgrading server {} to {}", server, newDistribution);
    uninstall(server);
    LocalKitManager localKitManager = new LocalKitManager(newDistribution);
    TcConfig config = tsaConfigurationContext.getTopology().findTcConfigOf(server.getServerSymbolicName());
    if (config instanceof SecureTcConfig) {
      installWithKitManager(server, ((SecureTcConfig) config).securityRootDirectoryFor(server.getServerSymbolicName()), localKitManager);
    } else {
      installWithKitManager(server, null, localKitManager);
    }
    return this;
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

    logger.info("Uninstalling TC server from {}", terracottaServer.getHostname());
    IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.uninstallTsa(instanceId, tsaConfigurationContext.getTopology(), terracottaServer, localKitManager
        .getKitInstallationName()));
  }

  public Tsa createAll() {
    tsaConfigurationContext.getTopology().getTcConfigs().stream()
        .flatMap(tcConfig -> tcConfig.getServers().stream())
        .map(server -> CompletableFuture.runAsync(() -> create(server)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
    return this;
  }

  public Tsa create(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    switch (terracottaServerState) {
      case STARTING:
      case STARTED_AS_ACTIVE:
      case STARTED_AS_PASSIVE:
        return this;
      case STOPPED:
        logger.info("Creating TC server on {}", terracottaServer.getHostname());
        IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.createTsa(instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment()));
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


  public Tsa start(TerracottaServer terracottaServer) {
    create(terracottaServer);
    IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.waitForTsaInState(instanceId, terracottaServer, of(STARTED_AS_ACTIVE, STARTED_AS_PASSIVE)));
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

  public Tsa stop(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == STOPPED) {
      return this;
    }
    logger.info("Stopping TC server on {}", terracottaServer.getHostname());
    IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.stopTsa(instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment()));
    return this;
  }

  public Tsa licenseAll() {
    licenseAll(null, false);
    return this;
  }

  public Tsa licenseAll(SecurityRootDirectory securityRootDirectory) {
    licenseAll(securityRootDirectory, false);
    return this;
  }

  public Tsa licenseAll(SecurityRootDirectory securityRootDirectory, boolean verbose) {
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
    logger.info("Licensing cluster from {}", terracottaServer.getHostname());
    IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.configureTsaLicense(instanceId, terracottaServer, tcConfigs, tsaConfigurationContext.getClusterName(), securityRootDirectory, tsaConfigurationContext.getTerracottaCommandLineEnvironment(), verbose));
    return this;
  }

  public TerracottaServerState getState(TerracottaServer terracottaServer) {
    return IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.getTsaState(instanceId, terracottaServer));
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
    Map<ServerSymbolicName, Integer> proxyTsaPorts = topology.isNetDisruptionEnabled() ? disruptionController.getProxyTsaPorts() : Collections.emptyMap();
    return topology.getDistribution().createDistributionController().tsaUri(topology.getServers(), proxyTsaPorts);
  }

  public RemoteFolder browse(TerracottaServer terracottaServer, String root) {
    String path = IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.CONTROLLER.getTsaInstallPath(instanceId, terracottaServer));
    return new RemoteFolder(ignite, terracottaServer.getHostname(), path, root);
  }

  public void uploadDataDirectories(File localRootPath) {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    List<TcConfig> tcConfigs = topology.getTcConfigs();
    for (TcConfig tcConfig : tcConfigs) {
      Collection<String> dataDirectories = tcConfig.getDataDirectories().values();
      List<TerracottaServer> servers = tcConfig.getServers();
      for (String directory : dataDirectories) {
        for (TerracottaServer server : servers) {
          try {
            File localFile = new File(localRootPath, server.getServerSymbolicName().getSymbolicName() + "/" + directory);
            browse(server, directory).upload(localFile);
          } catch (IOException ioe) {
            exceptions.add(ioe);
          }
        }
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error downloading TSA data directories");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public void downloadDataDirectories(File localRootPath) {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    List<TcConfig> tcConfigs = topology.getTcConfigs();
    for (TcConfig tcConfig : tcConfigs) {
      Map<String, String> dataDirectories = tcConfig.getDataDirectories();
      List<TerracottaServer> servers = tcConfig.getServers();
      for (TerracottaServer server : servers) {
        for (Map.Entry<String, String> entry : dataDirectories.entrySet()) {
          String directory = entry.getValue();
          try {
            browse(server, directory).downloadTo(new File(localRootPath + "/" + server.getServerSymbolicName().getSymbolicName(), directory));
          } catch (IOException ioe) {
            exceptions.add(ioe);
          }
        }
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error downloading TSA data directories");
      exceptions.forEach(re::addSuppressed);
      throw re;
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
