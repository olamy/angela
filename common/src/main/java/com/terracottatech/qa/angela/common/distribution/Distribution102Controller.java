package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.DisruptionProviderFactory;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.net.GroupMember;
import com.terracottatech.qa.angela.common.provider.TcConfigProvider;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import com.terracottatech.qa.angela.common.ClusterToolException;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.ExternalLoggers;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.ProcessUtil;
import com.terracottatech.qa.angela.common.util.TriggeringOutputStream;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
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
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.compile;

/**
 * @author Aurelien Broszniowski
 */
public class Distribution102Controller extends DistributionController {
  private final static Logger logger = LoggerFactory.getLogger(Distribution102Controller.class);
  private static final DisruptionProvider DISRUPTION_PROVIDER = DisruptionProviderFactory.getDefault();
  private final Map<ServerSymbolicName, Map<ServerSymbolicName, Disruptor>> disruptionLinks = new ConcurrentHashMap<>();
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
  public void disrupt(ServerSymbolicName serverSymbolicName, Collection<TerracottaServer> targets, boolean netDisruptionEnabled) {
    if (!netDisruptionEnabled) {
      throw new IllegalArgumentException("Topology not enabled for network disruption");
    }
    Map<ServerSymbolicName, Disruptor> disruptorMapPerSever = disruptionLinks.get(serverSymbolicName);
    for (TerracottaServer server : targets) {
      disruptorMapPerSever.get(server.getServerSymbolicName()).disrupt();
    }
  }

  @Override
  public void undisrupt(ServerSymbolicName serverSymbolicName, Collection<TerracottaServer> targets, boolean netDisruptionEnabled) {
    if (!netDisruptionEnabled) {
      throw new IllegalArgumentException("Topology not enabled for network disruption");
    }
    Map<ServerSymbolicName, Disruptor> disruptorMapPerSever = disruptionLinks.get(serverSymbolicName);
    for (TerracottaServer target : targets) {
      disruptorMapPerSever.get(target.getServerSymbolicName()).undisrupt();
    }
  }

  @Override
  public void removeDisruptionLinks(ServerSymbolicName serverSymbolicName, boolean netDisruptionEnabled) {
    if (netDisruptionEnabled) {
      Map<ServerSymbolicName, Disruptor> disruptorMapPerSever = disruptionLinks.get(serverSymbolicName);
      disruptorMapPerSever.values().forEach(DISRUPTION_PROVIDER::removeLink);
    }
  }

  @Override
  public TerracottaServerInstance.TerracottaServerInstanceProcess createTsa(TerracottaServer terracottaServer, File installLocation,
                                                                            Topology topology, TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs) {
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

    WatchedProcess watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(createTsaCommand(terracottaServer.getServerSymbolicName(), topology, installLocation, startUpArgs))
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
    return new TerracottaServerInstance.TerracottaServerInstanceProcess(stateRef, watchedProcess.getPid(), javaPid);
  }

  @Override
  public void stopTsa(ServerSymbolicName serverSymbolicName, File installLocation, TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
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
                + File.separator + "tools"
                + File.separator + "cluster-tool"
                + File.separator + "bin"
                + File.separator + "cluster-tool" + OS.INSTANCE.getShellExtension());
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
  public void configureTsaLicense(String clusterName, File location, String licensePath, List<TcConfig> tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv, boolean verbose) {
    Map<String, String> env = buildEnv(tcEnv);

    File tmpConfigDir = new File(location, "tmp-tc-configs");
    if (!tmpConfigDir.mkdir() && !tmpConfigDir.isDirectory()) {
      throw new RuntimeException("Error creating temporary cluster tool TC config folder : " + tmpConfigDir);
    }

    for (TcConfig tcConfig : tcConfigs) {
      tcConfig.writeTcConfigFile(tmpConfigDir);
    }

    List<String> commands = configureTsaLicenseCommand(location, licensePath, tcConfigs, clusterName, securityRootDirectory, verbose);

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
    if (distribution.getPackageType() == KIT) {
      return location.getAbsolutePath() + File.separator + "tools" + File.separator + "cluster-tool" + File.separator +
             "bin" + File.separator + "cluster-tool" + OS.INSTANCE.getShellExtension();
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return location.getAbsolutePath() + File.separator + "TerracottaDB" + File.separator + "tools" + File.separator +
             "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool" + OS.INSTANCE.getShellExtension();
    }
    throw new IllegalStateException("Can not define TSA licensing Command for distribution: " + distribution);
  }

  /**
   * Construct the Start Command with the Version, Tc Config file and server name
   *
   * @return List of String representing the start command and its parameters
   */
  private List<String> createTsaCommand(ServerSymbolicName serverSymbolicName, Topology topology, File installLocation, List<String> startUpArgs) {
    List<String> options = new ArrayList<>();
    // start command
    options.add(getTsaCreateExecutable(installLocation));

    // add -n if applicable
    if (!(serverSymbolicName.getSymbolicName().contains(":") || (serverSymbolicName.getSymbolicName().isEmpty()))) {
      options.add("-n");
      options.add(serverSymbolicName.getSymbolicName());
    }

    TcConfigProvider configurationProvider = (TcConfigProvider) topology.getConfigurationProvider();
    TcConfig tcConfig = configurationProvider.findTcConfig(serverSymbolicName);
    SecurityRootDirectory securityRootDirectory = null;
    if(tcConfig instanceof SecureTcConfig) {
      SecureTcConfig secureTcConfig = (SecureTcConfig) tcConfig;
      securityRootDirectory = secureTcConfig.securityRootDirectoryFor(serverSymbolicName);
    }
    TcConfig modifiedConfig = TcConfig.copy(configurationProvider.findTcConfig(serverSymbolicName));
    constructNetDisruptionLinks(modifiedConfig, serverSymbolicName, topology.isNetDisruptionEnabled());
    configurationProvider.setUpInstallation(modifiedConfig, serverSymbolicName, installLocation, securityRootDirectory);

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

  private void constructNetDisruptionLinks(TcConfig tcConfig, ServerSymbolicName serverSymbolicName, boolean netDisruptionEnabled) {
    if (netDisruptionEnabled) {
      List<GroupMember> members = tcConfig.retrieveGroupMembers(serverSymbolicName.getSymbolicName(), DISRUPTION_PROVIDER
          .isProxyBased());
      GroupMember thisMember = members.get(0);
      Map<ServerSymbolicName, Disruptor> disruptorLink = new HashMap<>();
      for (int i = 1; i < members.size(); ++i) {
        GroupMember otherMember = members.get(i);
        final InetSocketAddress src = new InetSocketAddress(thisMember.getHost(), otherMember.isProxiedMember() ? otherMember
            .getProxyPort() : thisMember.getGroupPort());
        final InetSocketAddress dest = new InetSocketAddress(otherMember.getHost(), otherMember.getGroupPort());
        disruptorLink.put(new ServerSymbolicName(otherMember.getServerName()), DISRUPTION_PROVIDER.createLink(src, dest));
      }
      disruptionLinks.put(serverSymbolicName, disruptorLink);
    }
  }

  private String getTsaCreateExecutable(File installLocation) {
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + File.separator + "server" + File.separator + "bin" + File.separator + "start-tc-server" +
             OS.INSTANCE.getShellExtension();
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
             + File.separator + "server" + File.separator + "bin" + File.separator + "start-tc-server" +
             OS.INSTANCE.getShellExtension();
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }


  @Override
  public TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment tcEnv) {
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
    WatchedProcess watchedProcess = new WatchedProcess<>(new ProcessExecutor()
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
    return new TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess(stateRef, wrapperPid, javaProcessPid);
  }

  @Override
  public void stopTms(File installLocation, TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
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
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + File.separator + "tools" + File.separator + "management" +
             File.separator + "bin" + File.separator + "start" + OS.INSTANCE.getShellExtension();
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
             + File.separator + "tools" + File.separator + "management" + File.separator + "bin" +
             File.separator + "start" + OS.INSTANCE.getShellExtension();
    }
    throw new IllegalStateException("Can not define TMS Start Command for distribution: " + distribution);
  }

  @Override
  public URI tsaUri(Collection<TerracottaServer> servers, Map<ServerSymbolicName, Integer> proxyTsaPorts) {
    return URI.create(servers
        .stream()
        .map(s -> s.getHostname() + ":" + proxyTsaPorts.getOrDefault(s.getServerSymbolicName(), s.getTsaPort()))
        .collect(Collectors.joining(",", "terracotta://", "")));
  }

  @Override
  public String clientJarsRootFolderName(Distribution distribution) {
    if (distribution.getPackageType() == KIT) {
      return "client";
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return "common" + File.separator + "lib";
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public String pluginJarsRootFolderName(Distribution distribution) {
    return "server" + File.separator + "plugins" + File.separator + "lib";
  }
}
