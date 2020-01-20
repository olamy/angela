package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.net.DisruptionController;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.provider.ConfigurationManager;
import com.terracottatech.qa.angela.common.provider.DynamicConfigManager;
import com.terracottatech.qa.angela.common.provider.TcConfigManager;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.CLUSTER_TOOL;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.CONFIG_TOOL;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.JCMD;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.SERVER_START_PREFIX;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.SERVER_STOP_PREFIX;
import static com.terracottatech.qa.angela.client.util.IgniteClientHelper.executeRemotely;
import static com.terracottatech.qa.angela.client.util.IgniteClientHelper.uploadKit;
import static com.terracottatech.qa.angela.common.AngelaProperties.KIT_INSTALLATION_DIR;
import static com.terracottatech.qa.angela.common.AngelaProperties.KIT_INSTALLATION_PATH;
import static com.terracottatech.qa.angela.common.AngelaProperties.SKIP_UNINSTALL;
import static com.terracottatech.qa.angela.common.AngelaProperties.getEitherOf;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_IN_DIAGNOSTIC_MODE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.util.RetryUtils.waitFor;
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
    return new ClusterTool(ignite, instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment(CLUSTER_TOOL));
  }

  public ConfigTool configTool(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      throw new IllegalStateException("Cannot control config tool: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return new ConfigTool(ignite, instanceId, terracottaServer, tsaConfigurationContext.getTerracottaCommandLineEnvironment(CONFIG_TOOL));
  }

  public String licensePath(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      throw new IllegalStateException("Cannot get license path: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.controller.getTsaLicensePath(instanceId, terracottaServer));
  }

  private void installAll() {
    Topology topology = tsaConfigurationContext.getTopology();
    ConfigurationManager configurationManager = topology.getConfigurationManager();
    for (TerracottaServer terracottaServer : configurationManager.getServers()) {
      install(terracottaServer, topology);
    }
  }

  private void install(TerracottaServer terracottaServer, Topology topology) {
    installWithKitManager(terracottaServer, topology, this.localKitManager);
  }

  private void installWithKitManager(TerracottaServer terracottaServer, Topology topology, LocalKitManager localKitManager) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState != TerracottaServerState.NOT_INSTALLED) {
      throw new IllegalStateException("Cannot install: server " + terracottaServer.getServerSymbolicName() + " in state " + terracottaServerState);
    }
    Distribution distribution = localKitManager.getDistribution();

    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));
    License license = tsaConfigurationContext.getLicense();

    String kitInstallationPath = getEitherOf(KIT_INSTALLATION_DIR, KIT_INSTALLATION_PATH);
    localKitManager.setupLocalInstall(license, kitInstallationPath, offline);

    boolean isRemoteInstallationSuccessful;
    IgniteCallable<Boolean> installTsaCallable = () -> Agent.controller.installTsa(instanceId, terracottaServer,
        offline, license, localKitManager.getKitInstallationName(), distribution, topology);
    if (kitInstallationPath == null) {
      logger.info("Attempting to remotely install if distribution already exists on {}", terracottaServer.getHostname());
      isRemoteInstallationSuccessful = executeRemotely(ignite, terracottaServer.getHostname(), installTsaCallable);
    } else {
      isRemoteInstallationSuccessful = false;
    }

    if (!isRemoteInstallationSuccessful) {
      try {
        logger.info("Uploading {} on {}", distribution, terracottaServer.getHostname());
        uploadKit(ignite, terracottaServer.getHostname(), instanceId, distribution, localKitManager.getKitInstallationName(), localKitManager.getKitInstallationPath().toFile());
        executeRemotely(ignite, terracottaServer.getHostname(), installTsaCallable);
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + terracottaServer.getHostname(), e);
      }
    }
  }

  public Tsa upgrade(TerracottaServer server, Distribution newDistribution) {
    logger.info("Upgrading server {} to {}", server, newDistribution);
    uninstall(server);
    LocalKitManager localKitManager = new LocalKitManager(newDistribution);
    installWithKitManager(server, tsaConfigurationContext.getTopology(), localKitManager);
    return this;
  }

  private void uninstallAll() {
    Topology topology = tsaConfigurationContext.getTopology();
    for (TerracottaServer terracottaServer : topology.getServers()) {
      uninstall(terracottaServer);
    }
  }

  private void uninstall(TerracottaServer terracottaServer) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    if (terracottaServerState == null) {
      return;
    }
    if (terracottaServerState != TerracottaServerState.STOPPED) {
      throw new IllegalStateException("Cannot uninstall: server " + terracottaServer.getServerSymbolicName() + " in state " + terracottaServerState);
    }

    logger.info("Uninstalling TC server from {}", terracottaServer.getHostname());
    IgniteRunnable uninstaller = () -> Agent.controller.uninstallTsa(instanceId, tsaConfigurationContext.getTopology(),
        terracottaServer, localKitManager.getKitInstallationName());
    executeRemotely(ignite, terracottaServer.getHostname(), uninstaller);
  }

  public Tsa createAll(String... startUpArgs) {
    tsaConfigurationContext.getTopology().getServers().stream()
        .map(server -> CompletableFuture.runAsync(() -> create(server, startUpArgs)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
    return this;
  }

  public Jcmd jcmd(TerracottaServer terracottaServer) {
    String whatFor = JCMD + terracottaServer.getServerSymbolicName().getSymbolicName();
    TerracottaCommandLineEnvironment tcEnv = tsaConfigurationContext.getTerracottaCommandLineEnvironment(whatFor);
    return new Jcmd(ignite, instanceId, terracottaServer, tcEnv);
  }

  public Tsa create(TerracottaServer terracottaServer, String... startUpArgs) {
    TerracottaServerState terracottaServerState = getState(terracottaServer);
    switch (terracottaServerState) {
      case STARTING:
      case STARTED_AS_ACTIVE:
      case STARTED_AS_PASSIVE:
      case STARTED_IN_DIAGNOSTIC_MODE:
        return this;
      case STOPPED:
        logger.info("Creating TC server on {}", terracottaServer.getHostname());
        IgniteRunnable tsaCreator = () -> {
          String whatFor = SERVER_START_PREFIX + terracottaServer.getServerSymbolicName().getSymbolicName();
          TerracottaCommandLineEnvironment cliEnv = tsaConfigurationContext.getTerracottaCommandLineEnvironment(whatFor);
          Agent.controller.createTsa(instanceId, terracottaServer, cliEnv, Arrays.asList(startUpArgs));
        };
        executeRemotely(ignite, terracottaServer.getHostname(), tsaCreator);
        return this;
    }
    throw new IllegalStateException("Cannot create: server " + terracottaServer.getServerSymbolicName() + " in state " + terracottaServerState);
  }

  public Tsa startAll(String... startUpArgs) {
    tsaConfigurationContext.getTopology().getServers().stream()
        .map(server -> CompletableFuture.runAsync(() -> start(server, startUpArgs)))
        .reduce(CompletableFuture::allOf).ifPresent(CompletableFuture::join);
    return this;
  }

  public DisruptionController disruptionController() {
    return disruptionController;
  }


  public Tsa start(TerracottaServer terracottaServer, String... startUpArgs) {
    create(terracottaServer, startUpArgs);
    executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.controller.waitForTsaInState(instanceId, terracottaServer, of(STARTED_AS_ACTIVE, STARTED_AS_PASSIVE, STARTED_IN_DIAGNOSTIC_MODE)));
    return this;
  }

  public Tsa stopAll() {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    for (TerracottaServer terracottaServer : topology.getServers()) {
      try {
        stop(terracottaServer);
      } catch (Exception e) {
        exceptions.add(e);
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
    executeRemotely(ignite, terracottaServer.getHostname(), () -> {
      String whatFor = SERVER_STOP_PREFIX + terracottaServer.getServerSymbolicName().getSymbolicName();
      TerracottaCommandLineEnvironment cliEnv = tsaConfigurationContext.getTerracottaCommandLineEnvironment(whatFor);
      Agent.controller.stopTsa(instanceId, terracottaServer, cliEnv);
    });
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
    ConfigurationManager configurationManager = tsaConfigurationContext.getTopology().getConfigurationManager();
    Set<ServerSymbolicName> notStartedServers = new HashSet<>();
    for (TerracottaServer terracottaServer : configurationManager.getServers()) {
      TerracottaServerState terracottaServerState = getState(terracottaServer);
      if (terracottaServerState != STARTED_AS_ACTIVE && terracottaServerState != STARTED_AS_PASSIVE) {
        notStartedServers.add(terracottaServer.getServerSymbolicName());
      }
    }
    if (!notStartedServers.isEmpty()) {
      throw new IllegalStateException("The following Terracotta servers are not started : " + notStartedServers);
    }

    if (configurationManager instanceof TcConfigManager) {
      final Map<ServerSymbolicName, Integer> proxyTsaPorts;
      if (tsaConfigurationContext.getTopology().isNetDisruptionEnabled()) {
        proxyTsaPorts = disruptionController.updateTsaPortsWithProxy(tsaConfigurationContext.getTopology());
      } else {
        proxyTsaPorts = new HashMap<>();
      }

      TerracottaServer terracottaServer = tsaConfigurationContext.getTopology().getConfigurationManager().getServers().get(0);
      logger.info("Configuring cluster from {}", terracottaServer.getHostname());
      executeRemotely(ignite, terracottaServer.getHostname(), () -> {
        TerracottaCommandLineEnvironment cliEnv = tsaConfigurationContext.getTerracottaCommandLineEnvironment(CLUSTER_TOOL);
        Agent.controller.configure(instanceId, terracottaServer, tsaConfigurationContext.getTopology(), proxyTsaPorts, tsaConfigurationContext.getClusterName(), securityRootDirectory, cliEnv, verbose);
      });
      return this;
    } else {
      throw new IllegalStateException();
    }
  }

  public Tsa activateAll() {
    Topology topology = tsaConfigurationContext.getTopology();
    List<List<TerracottaServer>> stripes = topology.getStripes();
    ConfigurationManager configurationManager = topology.getConfigurationManager();
    Set<ServerSymbolicName> notStartedServers = new HashSet<>();
    for (TerracottaServer terracottaServer : configurationManager.getServers()) {
      TerracottaServerState terracottaServerState = getState(terracottaServer);
      if (terracottaServerState != STARTED_IN_DIAGNOSTIC_MODE) {
        notStartedServers.add(terracottaServer.getServerSymbolicName());
      }
    }
    if (!notStartedServers.isEmpty()) {
      throw new IllegalStateException("The following Terracotta servers are not started : " + notStartedServers);
    }

    if (configurationManager instanceof DynamicConfigManager) {
      TerracottaServer terracottaServer = configurationManager.getServers().get(0);
      logger.info("Activating cluster from {}", terracottaServer.getHostname());
      executeRemotely(ignite, terracottaServer.getHostname(), () -> {
        TerracottaCommandLineEnvironment cliEnv = tsaConfigurationContext.getTerracottaCommandLineEnvironment(CONFIG_TOOL);
        Agent.controller.configure(instanceId, terracottaServer, topology, null, tsaConfigurationContext.getClusterName(), null, cliEnv, false);
      });

      final int maxRetryCount = 5;
      final int maxWaitTimeMillis = 5000;
      if (!waitFor(() -> getActives().size() == stripes.size(), maxRetryCount, maxWaitTimeMillis)) {
        throw new RuntimeException(
            String.format(
                "Tried for %d times (%dms), but all stripes did not get actives",
                maxRetryCount,
                maxWaitTimeMillis
            )
        );
      }

      if (!waitFor(() -> getPassives().size() == topology.getServers().size() - getActives().size(), maxRetryCount, maxWaitTimeMillis)) {
        throw new RuntimeException(
            String.format(
                "Tried for %d times (%dms), but all stripes did not get the expected number of passives",
                maxRetryCount,
                maxWaitTimeMillis
            )
        );
      }
      return this;
    } else {
      throw new IllegalStateException();
    }
  }

  public TerracottaServerState getState(TerracottaServer terracottaServer) {
    return executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.controller.getTsaState(instanceId, terracottaServer));
  }

  public Collection<TerracottaServer> getStarted() {
    Collection<TerracottaServer> allRunningServers = new ArrayList<>();
    allRunningServers.addAll(getActives());
    allRunningServers.addAll(getPassives());
    allRunningServers.addAll(getDiagnosticModeSevers());
    return allRunningServers;
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

  public Collection<TerracottaServer> getDiagnosticModeSevers() {
    Collection<TerracottaServer> result = new ArrayList<>();
    for (TerracottaServer terracottaServer : tsaConfigurationContext.getTopology().getServers()) {
      if (getState(terracottaServer) == STARTED_IN_DIAGNOSTIC_MODE) {
        result.add(terracottaServer);
      }
    }
    return result;
  }

  public TerracottaServer getDiagnosticModeServer() {
    Collection<TerracottaServer> servers = getDiagnosticModeSevers();
    switch (servers.size()) {
      case 0:
        return null;
      case 1:
        return servers.iterator().next();
      default:
        throw new IllegalStateException("There is more than one diagnostic mode server, found " + servers.size());
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
    String path = executeRemotely(ignite, terracottaServer.getHostname(), () -> Agent.controller.getTsaInstallPath(instanceId, terracottaServer));
    return new RemoteFolder(ignite, terracottaServer.getHostname(), path, root);
  }

  public void uploadPlugin(File localPluginFile) {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    for (TerracottaServer server : topology.getServers()) {
      try {
        browse(server, topology.getDistribution().createDistributionController().pluginJarsRootFolderName(topology.getDistribution())).upload(localPluginFile);
      } catch (IOException ioe) {
        exceptions.add(ioe);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error uploading TSA plugin");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public void uploadDataDirectories(File localRootPath) {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    ConfigurationManager configurationManager = topology.getConfigurationManager();
    if (configurationManager instanceof TcConfigManager) {
      TcConfigManager tcConfigProvider = (TcConfigManager) configurationManager;
      List<TcConfig> tcConfigs = tcConfigProvider.getTcConfigs();
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
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error uploading TSA data directories");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public void downloadDataDirectories(File localRootPath) {
    List<Exception> exceptions = new ArrayList<>();

    Topology topology = tsaConfigurationContext.getTopology();
    ConfigurationManager configurationManager = topology.getConfigurationManager();
    if (configurationManager instanceof TcConfigManager) {
      TcConfigManager tcConfigProvider = (TcConfigManager) configurationManager;
      List<TcConfig> tcConfigs = tcConfigProvider.getTcConfigs();
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
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error downloading TSA data directories");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public List<ConfigToolExecutionResult> attachStripe(TerracottaServer... newServers) {
    if (newServers == null || newServers.length == 0) {
      throw new IllegalArgumentException("Servers list should be non-null and non-empty");
    }

    for (TerracottaServer server : newServers) {
      install(server, tsaConfigurationContext.getTopology());
      start(server);
    }
    tsaConfigurationContext.getTopology().addStripe(newServers);

    List<ConfigToolExecutionResult> results = new ArrayList<>();
    if (newServers.length > 1) {
      List<String> command = new ArrayList<>();
      command.add("attach");
      command.add("-t");
      command.add("node");
      command.add("-d");
      command.add(newServers[0].getHostPort());
      for (int i = 1; i < newServers.length; i++) {
        command.add("-s");
        command.add(newServers[i].getHostPort());
      }
      ConfigToolExecutionResult result = configTool(newServers[0]).executeCommand(command.toArray(new String[0]));
      if (result.getExitStatus() != 0) {
        throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
      }
      results.add(result);
    }

    List<String> command = new ArrayList<>();
    command.add("attach");
    command.add("-t");
    command.add("stripe");

    List<List<TerracottaServer>> stripes = tsaConfigurationContext.getTopology().getStripes();
    TerracottaServer existingServer = stripes.get(0).get(0);
    command.add("-d");
    command.add(existingServer.getHostPort());
    for (TerracottaServer newServer : newServers) {
      command.add("-s");
      command.add(newServer.getHostPort());
    }

    ConfigToolExecutionResult result = configTool(existingServer).executeCommand(command.toArray(new String[0]));
    if (result.getExitStatus() != 0) {
      throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
    }
    results.add(result);
    return results;
  }

  public ConfigToolExecutionResult detachStripe(int stripeIndex) {
    List<List<TerracottaServer>> stripes = tsaConfigurationContext.getTopology().getStripes();
    if (stripeIndex < -1 || stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("stripeIndex should be a non-negative integer less than stripe count");
    }

    if (stripes.size() == 1) {
      throw new IllegalArgumentException("Cannot delete the only stripe from cluster");
    }

    List<String> command = new ArrayList<>();
    command.add("detach");
    command.add("-t");
    command.add("stripe");

    List<TerracottaServer> toDetachStripe = stripes.remove(stripeIndex);
    TerracottaServer destination = stripes.get(0).get(0);
    command.add("-d");
    command.add(destination.getHostPort());

    command.add("-s");
    command.add(toDetachStripe.get(0).getHostPort());

    ConfigToolExecutionResult result = configTool(destination).executeCommand(command.toArray(new String[0]));
    if (result.getExitStatus() != 0) {
      throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
    }

    for (TerracottaServer detachServer : toDetachStripe) {
      stop(detachServer);
    }
    tsaConfigurationContext.getTopology().removeStripe(stripeIndex);
    return result;
  }

  public ConfigToolExecutionResult attachNodes(int stripeIndex, TerracottaServer... newServers) {
    List<List<TerracottaServer>> stripes = tsaConfigurationContext.getTopology().getStripes();
    if (stripeIndex < -1 || stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("stripeIndex should be a non-negative integer less than stripe count");
    }

    if (newServers == null || newServers.length == 0) {
      throw new IllegalArgumentException("Servers list should be non-null and non-empty");
    }

    for (TerracottaServer newServer : newServers) {
      install(newServer, tsaConfigurationContext.getTopology());
      start(newServer);
      tsaConfigurationContext.getTopology().addServer(stripeIndex, newServer);
    }

    List<String> command = new ArrayList<>();
    command.add("attach");
    command.add("-t");
    command.add("node");

    TerracottaServer existingServer = stripes.get(stripeIndex).get(0);
    command.add("-d");
    command.add(existingServer.getHostPort());

    for (TerracottaServer newServer : newServers) {
      command.add("-s");
      command.add(newServer.getHostPort());
    }

    ConfigToolExecutionResult result = configTool(existingServer).executeCommand(command.toArray(new String[0]));
    if (result.getExitStatus() != 0) {
      throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
    }
    return result;
  }

  public ConfigToolExecutionResult detachNode(int stripeIndex, int serverIndex) {
    List<List<TerracottaServer>> stripes = tsaConfigurationContext.getTopology().getStripes();
    if (stripeIndex < -1 || stripeIndex >= stripes.size()) {
      throw new IllegalArgumentException("stripeIndex should be a non-negative integer less than stripe count");
    }

    List<TerracottaServer> servers = stripes.remove(stripeIndex);
    if (serverIndex < -1 || serverIndex >= servers.size()) {
      throw new IllegalArgumentException("serverIndex should be a non-negative integer less than server count");
    }

    TerracottaServer toDetach = servers.remove(serverIndex);
    if (servers.size() == 0 && stripes.size() == 0) {
      throw new IllegalArgumentException("Cannot delete the only server from the cluster");
    }

    TerracottaServer destination;
    if (stripes.size() != 0) {
      destination = stripes.get(0).get(0);
    } else {
      destination = servers.get(0);
    }

    List<String> command = new ArrayList<>();
    command.add("detach");
    command.add("-t");
    command.add("node");
    command.add("-d");
    command.add(destination.getHostPort());
    command.add("-s");
    command.add(toDetach.getHostPort());

    ConfigToolExecutionResult result = configTool(destination).executeCommand(command.toArray(new String[0]));
    if (result.getExitStatus() != 0) {
      throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
    }

    stop(toDetach);
    tsaConfigurationContext.getTopology().removeServer(stripeIndex, serverIndex);
    return result;
  }

  public Tsa attachAll() {
    List<List<TerracottaServer>> stripes = tsaConfigurationContext.getTopology().getStripes();

    // Attach all servers in a stripe to form individual stripes
    for (List<TerracottaServer> stripe : stripes) {
      if (stripe.size() > 1) {
        List<String> command = new ArrayList<>();
        command.add("attach");
        command.add("-t");
        command.add("node");
        command.add("-d");
        command.add(stripe.get(0).getHostPort());
        for (int i = 1; i < stripe.size(); i++) {
          command.add("-s");
          command.add(stripe.get(i).getHostPort());
        }
        ConfigToolExecutionResult result = configTool(stripe.get(0)).executeCommand(command.toArray(new String[0]));
        if (result.getExitStatus() != 0) {
          throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
        }
      }
    }

    if (stripes.size() > 1) {
      // Attach all stripes together to form the cluster
      List<String> command = new ArrayList<>();
      command.add("attach");
      command.add("-t");
      command.add("stripe");
      command.add("-d");
      command.add(stripes.get(0).get(0).getHostPort());

      for (int i = 1; i < stripes.size(); i++) {
        List<TerracottaServer> stripe = stripes.get(i);
        command.add("-s");
        command.add(stripe.get(0).getHostPort());
      }

      ConfigToolExecutionResult result = configTool(stripes.get(0).get(0)).executeCommand(command.toArray(new String[0]));
      if (result.getExitStatus() != 0) {
        throw new RuntimeException("ConfigTool::executeCommand with command parameters failed with: " + result);
      }
    }

    return this;
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
    if (!Boolean.parseBoolean(SKIP_UNINSTALL.getValue())) {
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
