package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.ClusterToolException;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.TerracottaServerInstance.TerracottaServerInstanceProcess;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.provider.ConfigurationManager;
import com.terracottatech.qa.angela.common.provider.TcConfigManager;
import com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.ExternalLoggers;
import com.terracottatech.qa.angela.common.util.HostPort;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.ProcessUtil;
import com.terracottatech.qa.angela.common.util.TriggeringOutputStream;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.terracottatech.qa.angela.common.AngelaProperties.TMS_FULL_LOGGING;
import static com.terracottatech.qa.angela.common.AngelaProperties.TSA_FULL_LOGGING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidHost;
import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv4;
import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv6;
import static java.io.File.separator;
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.compile;

/**
 * @author Aurelien Broszniowski
 */
public class Distribution102Controller extends DistributionController {
  private final static Logger logger = LoggerFactory.getLogger(Distribution102Controller.class);
  private final boolean tsaFullLogging = Boolean.parseBoolean(TSA_FULL_LOGGING.getValue());
  private final boolean tmsFullLogging = Boolean.parseBoolean(TMS_FULL_LOGGING.getValue());

  Distribution102Controller(Distribution distribution) {
    super(distribution);
    Version version = distribution.getVersion();
    if (version.getMajor() != 3 && version.getMajor() != 10) {
      throw new IllegalStateException(getClass().getSimpleName() + " cannot work with distribution version " + version);
    }
  }

  @Override
  public TerracottaServerInstanceProcess createTsa(TerracottaServer terracottaServer, File installLocation, Topology topology,
                                                   Map<ServerSymbolicName, Integer> proxiedPorts, TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs) {
    Map<String, String> env = buildEnv(tcEnv);

    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    AtomicInteger javaPid = new AtomicInteger(-1);

    TriggeringOutputStream serverLogOutputStream = TriggeringOutputStream.triggerOn(
        compile("^.*\\QTerracotta Server instance has started up as ACTIVE\\E.*$"), mr -> stateRef.set(STARTED_AS_ACTIVE)
    ).andTriggerOn(
        compile("^.*\\QMoved to State[ PASSIVE-STANDBY ]\\E.*$"), mr -> stateRef.set(STARTED_AS_PASSIVE)
    ).andTriggerOn(
        compile("^.*PID is (\\d+).*$"), mr -> {
          javaPid.set(parseInt(mr.group(1)));
          stateRef.compareAndSet(STOPPED, STARTING);
        }
    ).andTriggerOn(
        tsaFullLogging ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"), mr -> ExternalLoggers.tsaLogger.info("[{}] {}", terracottaServer
            .getServerSymbolicName().getSymbolicName(), mr.group())
    );

    WatchedProcess<TerracottaServerState> watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(createTsaCommand(terracottaServer.getServerSymbolicName(), terracottaServer.getId(), topology, proxiedPorts, installLocation, startUpArgs))
        .directory(installLocation)
        .environment(env)
        .redirectError(System.err)
        .redirectOutput(serverLogOutputStream), stateRef, STOPPED);

    while (javaPid.get() == -1 && watchedProcess.isAlive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    if (!watchedProcess.isAlive()) {
      throw new RuntimeException("Terracotta server process died in its infancy : " + terracottaServer.getServerSymbolicName());
    }
    return new TerracottaServerInstanceProcess(stateRef, watchedProcess.getPid(), javaPid);
  }

  @Override
  public void stopTsa(ServerSymbolicName serverSymbolicName, File installLocation, TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    logger.debug("Destroying TC server process for {}", serverSymbolicName);
    for (Number pid : terracottaServerInstanceProcess.getPids()) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(pid.intValue());
      } catch (Exception e) {
        logger.error("Could not destroy TC server process with PID '{}'", pid, e);
      }
    }
  }

  @Override
  public ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    List<String> command = new ArrayList<>();
    command.add(installLocation
        + separator + "tools"
        + separator + "cluster-tool"
        + separator + "bin"
        + separator + "cluster-tool" + OS.INSTANCE.getShellExtension());
    command.addAll(Arrays.asList(arguments));

    try {
      ProcessResult processResult = new ProcessExecutor(command)
          .environment(buildEnv(tcEnv))
          .redirectErrorStream(true)
          .readOutput(true)
          .execute();
      return new ClusterToolExecutionResult(processResult.getExitValue(), processResult.getOutput().getLines());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ConfigToolExecutionResult invokeConfigTool(File installLocation, TerracottaCommandLineEnvironment env, String... arguments) {
    throw new UnsupportedOperationException("Config Tool is supported only for a dynamically-configured cluster");
  }

  @Override
  public void configure(String clusterName, File location, String licensePath, Topology topology, Map<ServerSymbolicName, Integer> proxyTsaPorts, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv, boolean verbose) {
    Map<String, String> env = buildEnv(tcEnv);

    File tmpConfigDir = new File(location, "tmp-tc-configs");
    if (!tmpConfigDir.mkdir() && !tmpConfigDir.isDirectory()) {
      throw new RuntimeException("Error creating temporary cluster tool TC config folder : " + tmpConfigDir);
    }
    ConfigurationManager configurationProvider = topology.getConfigurationManager();
    TcConfigManager tcConfigProvider = (TcConfigManager) configurationProvider;
    List<TcConfig> tcConfigs = tcConfigProvider.getTcConfigs();
    List<TcConfig> modifiedConfigs = new ArrayList<>();
    for (TcConfig tcConfig : tcConfigs) {
      TcConfig modifiedConfig = TcConfig.copy(tcConfig);
      if (!proxyTsaPorts.isEmpty()) {
        modifiedConfig.updateServerTsaPort(proxyTsaPorts);
      }
      modifiedConfig.writeTcConfigFile(tmpConfigDir);
      modifiedConfigs.add(modifiedConfig);
    }

    List<String> commands = configureTsaLicenseCommand(location, licensePath, modifiedConfigs, clusterName, securityRootDirectory, verbose);

    logger.debug("Licensing commands: {}", commands);
    logger.debug("Licensing command line environment: {}", tcEnv);

    ProcessExecutor executor = new ProcessExecutor().redirectOutput(Slf4jStream.of(ExternalLoggers.clusterToolLogger)
        .asInfo())
        .command(commands).directory(location).environment(env);

    ProcessResult processResult;
    try {
      StartedProcess startedProcess = executor.start();
      processResult = startedProcess.getFuture().get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        FileUtils.deleteDirectory(tmpConfigDir);
      } catch (IOException ioe) {
        logger.error("Error deleting temporary cluster tool TC config files", ioe);
      }
    }

    if (processResult.getExitValue() != 0) {
      throw new ClusterToolException("Error when installing the cluster license", commands.toString(), processResult.getExitValue());
    }
  }

  private List<String> configureTsaLicenseCommand(File location, String licensePath, List<TcConfig> tcConfigs, String clusterName, SecurityRootDirectory securityRootDirectory, boolean verbose) {
    List<String> command = new ArrayList<>();

    command.add(getConfigureTsaExecutable(location));

    if (securityRootDirectory != null) {
      Path securityRootDirectoryPath = location.toPath()
          .resolve("cluster-tool-security")
          .resolve("security-root-directory");
      securityRootDirectory.createSecurityRootDirectory(securityRootDirectoryPath);
      logger.debug("Using SecurityRootDirectory " + securityRootDirectoryPath);
      command.add("-srd");
      command.add(securityRootDirectoryPath.toString());
    }
    if (verbose) {
      command.add("-v");
    }
    command.add("configure");
    command.add("-n");
    command.add(clusterName);
    command.add("-l");
    command.add(licensePath);

    for (TcConfig tcConfig : tcConfigs) {
      command.add(tcConfig.getPath());
    }

    return command;
  }

  private String getConfigureTsaExecutable(File location) {
    String execPath = "tools" + separator + "cluster-tool" + separator + "bin" + separator + "cluster-tool" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return location.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return location.getAbsolutePath() + separator + terracottaInstallationRoot() + separator + execPath;
    }
    throw new IllegalStateException("Can not define TSA licensing Command for distribution: " + distribution);
  }

  /**
   * Construct the Start Command with the Version, Tc Config file and server name
   *
   * @return List of String representing the start command and its parameters
   */
  private List<String> createTsaCommand(ServerSymbolicName serverSymbolicName,
                                        UUID serverId,
                                        Topology topology,
                                        Map<ServerSymbolicName, Integer> proxiedPorts,
                                        File installLocation,
                                        List<String> startUpArgs) {
    List<String> options = new ArrayList<>();
    // start command
    options.add(getTsaCreateExecutable(installLocation));

    String symbolicName = serverSymbolicName.getSymbolicName();
    if (isValidHost(symbolicName) || isValidIPv4(symbolicName) || isValidIPv6(symbolicName) || symbolicName.isEmpty()) {
      // add -n if applicable
      options.add("-n");
      options.add(symbolicName);
    }

    TcConfigManager configurationProvider = (TcConfigManager) topology.getConfigurationManager();
    TcConfig tcConfig = configurationProvider.findTcConfig(serverId);
    SecurityRootDirectory securityRootDirectory = null;
    if (tcConfig instanceof SecureTcConfig) {
      SecureTcConfig secureTcConfig = (SecureTcConfig) tcConfig;
      securityRootDirectory = secureTcConfig.securityRootDirectoryFor(serverSymbolicName);
    }
    TcConfig modifiedConfig = TcConfig.copy(configurationProvider.findTcConfig(serverId));
    configurationProvider.setUpInstallation(modifiedConfig, serverSymbolicName, serverId, proxiedPorts, installLocation, securityRootDirectory);

    // add -f if applicable
    if (modifiedConfig.getPath() != null) {
      options.add("-f");
      options.add(modifiedConfig.getPath());
    }

    options.addAll(startUpArgs);

    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.debug("Create TSA command = {}", sb.toString());

    return options;
  }

  private String getTsaCreateExecutable(File installLocation) {
    String execPath = "server" + separator + "bin" + separator + "start-tc-server" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + separator + terracottaInstallationRoot() + separator + execPath;
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }


  @Override
  public TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment tcEnv) {
    Map<String, String> env = buildEnv(tcEnv);

    AtomicReference<TerracottaManagementServerState> stateRef = new AtomicReference<>(TerracottaManagementServerState.STOPPED);
    AtomicInteger javaPid = new AtomicInteger(-1);

    TriggeringOutputStream outputStream = TriggeringOutputStream.triggerOn(
        compile("^.*\\Qstarted on port\\E.*$"), mr -> stateRef.set(TerracottaManagementServerState.STARTED)
    ).andTriggerOn(
        compile("^.*\\QStarting TmsApplication\\E.*with PID (\\d+).*$"), mr -> javaPid.set(parseInt(mr.group(1)))
    ).andTriggerOn(
        tmsFullLogging ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"), mr -> ExternalLoggers.tmsLogger.info(mr.group())
    );

    WatchedProcess<TerracottaManagementServerState> watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(startTmsCommand(installLocation))
        .directory(installLocation)
        .environment(env)
        .redirectError(System.err)
        .redirectOutput(outputStream), stateRef, TerracottaManagementServerState.STOPPED);

    while ((javaPid.get() == -1 || stateRef.get() == TerracottaManagementServerState.STOPPED) && watchedProcess.isAlive()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    if (!watchedProcess.isAlive()) {
      throw new RuntimeException("TMS process died before reaching STARTED state");
    }
    int wrapperPid = watchedProcess.getPid();
    int javaProcessPid = javaPid.get();
    return new TerracottaManagementServerInstanceProcess(stateRef, wrapperPid, javaProcessPid);
  }

  @Override
  public void stopTms(File installLocation, TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    logger.debug("Destroying TMS process");
    for (Number pid : terracottaServerInstanceProcess.getPids()) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(pid.intValue());
      } catch (Exception e) {
        logger.error("Could not destroy TMS process {}", pid, e);
      }
    }
  }

  /**
   * Construct the Start Command with the Version
   *
   * @return String[] representing the start command and its parameters
   */
  private List<String> startTmsCommand(File installLocation) {
    List<String> options = new ArrayList<>();
    // start command
    options.add(getStartTmsExecutable(installLocation));

    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.debug(" Start TMS command = {}", sb.toString());

    return options;
  }

  private String getStartTmsExecutable(File installLocation) {
    String execPath = "tools" + separator + "management" + separator + "bin" + separator + "start" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + separator + terracottaInstallationRoot() + separator + execPath;
    }
    throw new IllegalStateException("Can not define TMS Start Command for distribution: " + distribution);
  }

  @Override
  public URI tsaUri(Collection<TerracottaServer> servers, Map<ServerSymbolicName, Integer> proxyTsaPorts) {
    return URI.create(servers
        .stream()
        .map(s -> new HostPort(s.getHostname(), proxyTsaPorts.getOrDefault(s.getServerSymbolicName(), s.getTsaPort())).getHostPort())
        .collect(Collectors.joining(",", "terracotta://", "")));
  }

  @Override
  public String clientJarsRootFolderName(Distribution distribution) {
    if (distribution.getPackageType() == KIT) {
      return "client";
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return "common" + separator + "lib";
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public String pluginJarsRootFolderName(Distribution distribution) {
    return "server" + separator + "plugins" + separator + "lib";
  }

  @Override
  public String terracottaInstallationRoot() {
    return "TerracottaDB";
  }
}
