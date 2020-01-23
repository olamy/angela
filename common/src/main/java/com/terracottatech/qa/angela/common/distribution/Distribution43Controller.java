package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance.TerracottaServerInstanceProcess;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.provider.ConfigurationManager;
import com.terracottatech.qa.angela.common.provider.TcConfigManager;
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
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.terracottatech.qa.angela.common.AngelaProperties.TSA_FULL_LOGGING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidHost;
import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv4;
import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv6;
import static java.io.File.separator;
import static java.util.regex.Pattern.compile;

/**
 * @author Aurelien Broszniowski
 */
public class Distribution43Controller extends DistributionController {
  private final static Logger logger = LoggerFactory.getLogger(Distribution43Controller.class);

  private final boolean tsaFullLogging = Boolean.parseBoolean(TSA_FULL_LOGGING.getValue());

  Distribution43Controller(Distribution distribution) {
    super(distribution);
    Version version = distribution.getVersion();
    if (version.getMajor() != 4) {
      throw new IllegalStateException(getClass().getSimpleName() + " cannot work with distribution version " + version);
    }
  }

  @Override
  public TerracottaServerInstanceProcess createTsa(TerracottaServer terracottaServer, File installLocation, Topology topology,
                                                   Map<ServerSymbolicName, Integer> proxiedPorts, TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs) {
    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    AtomicReference<TerracottaServerState> tempStateRef = new AtomicReference<>(STOPPED);

    TriggeringOutputStream serverLogOutputStream = TriggeringOutputStream.triggerOn(
        compile("^.*\\QTerracotta Server instance has started up as ACTIVE\\E.*$"), mr -> {
          if (stateRef.get() == STOPPED) {
            tempStateRef.set(STARTED_AS_ACTIVE);
          } else {
            stateRef.set(STARTED_AS_ACTIVE);
          }
        }
    ).andTriggerOn(
        compile("^.*\\QMoved to State[ PASSIVE-STANDBY ]\\E.*$"), mr -> tempStateRef.set(STARTED_AS_PASSIVE)
    ).andTriggerOn(
        compile("^.*\\QManagement server started\\E.*$"), mr -> stateRef.set(tempStateRef.get())
    ).andTriggerOn(
        tsaFullLogging ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"), mr -> ExternalLoggers.tsaLogger.info("[{}] {}", terracottaServer
            .getServerSymbolicName().getSymbolicName(), mr.group())
    );

    // add an identifiable ID to the JVM's system properties
    String serverUuid = UUID.randomUUID().toString();
    Map<String, String> env = buildEnv(tcEnv);
    env.compute("JAVA_OPTS", (key, value) -> {
      String prop = " -Dangela.processIdentifier=" + serverUuid;
      return value == null ? prop : value + prop;
    });

    WatchedProcess<TerracottaServerState> watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(createTsaCommand(terracottaServer.getServerSymbolicName(), terracottaServer.getId(), topology.getConfigurationManager(), proxiedPorts, installLocation, startUpArgs))
        .directory(installLocation)
        .environment(env)
        .redirectError(System.err)
        .redirectOutput(serverLogOutputStream), stateRef, STOPPED);

    int wrapperPid = watchedProcess.getPid();
    Number javaPid = findWithJcmdJavaPidOf(serverUuid, tcEnv);
    return new TerracottaServerInstanceProcess(stateRef, wrapperPid, javaPid);
  }

  private Number findWithJcmdJavaPidOf(String serverUuid, TerracottaCommandLineEnvironment tcEnv) {
    String javaHome = javaLocationResolver.resolveJavaLocation(tcEnv).getHome();

    List<String> cmdLine = new ArrayList<>();
    if (OS.INSTANCE.isWindows()) {
      cmdLine.add(javaHome + "\\bin\\jcmd.exe");
    } else {
      cmdLine.add(javaHome + "/bin/jcmd");
    }
    cmdLine.add("com.tc.server.TCServerMain");
    cmdLine.add("VM.system_properties");

    final int retries = 100;
    for (int i = 0; i < retries; i++) {
      ProcessResult processResult;
      try {
        processResult = new ProcessExecutor(cmdLine)
            .redirectErrorStream(true)
            .readOutput(true)
            .execute();
      } catch (Exception e) {
        logger.warn("Unable to get server pid with jcmd", e);
        return null;
      }

      if (processResult.getExitValue() == 0) {
        return parseOutputAndFindUuid(processResult.getOutput().getLines(), serverUuid);
      }

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // warn on the last loop
      if (i == (retries - 1)) {
        logger.warn("Unable to get server pid with jcmd (rc={})", processResult.getExitValue());
        logger.warn("{}", processResult.getOutput().getString());
      }
    }

    return null;
  }

  private Number parseOutputAndFindUuid(List<String> lines, String serverUuid) {
    int pid = 0;
    for (String line : lines) {
      if (line.endsWith(":")) {
        try {
          pid = Integer.parseInt(line.substring(0, line.length() - 1));
        } catch (NumberFormatException e) {
          // false positive, skip
          continue;
        }
      }

      if (line.equals("angela.processIdentifier=" + serverUuid)) {
        return pid;
      }
    }
    return null;
  }

  @Override
  public void stopTsa(ServerSymbolicName serverSymbolicName, File installLocation, TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    ProcessExecutor executor = new ProcessExecutor()
        .command(stopTsaCommand(serverSymbolicName, installLocation))
        .directory(installLocation)
        .environment(buildEnv(tcEnv))
        .redirectError(Slf4jStream.of(ExternalLoggers.tsaLogger).asInfo())
        .redirectOutput(Slf4jStream.of(ExternalLoggers.tsaLogger).asInfo());

    try {
      logger.debug("Calling stop command for server {}", serverSymbolicName);
      executor.start();

      for (int i = 0; i < 100; i++) {
        if (terracottaServerInstanceProcess.getState() == STOPPED) {
          return;
        }
        Thread.sleep(100);
      }

    } catch (Exception e) {
      throw new RuntimeException("Can not stop Terracotta server process " + serverSymbolicName, e);
    }

    logger.debug("Destroying TC server process for {}", serverSymbolicName);
    for (Number pid : terracottaServerInstanceProcess.getPids()) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(pid.intValue());

        for (int i = 0; i < 100; i++) {
          if (terracottaServerInstanceProcess.getState() == STOPPED) {
            return;
          }
          Thread.sleep(100);
        }
      } catch (Exception e) {
        logger.error("Could not destroy process {}", pid, e);
      }
    }

    throw new RuntimeException("Terracotta server [" + serverSymbolicName + "] could not be stopped.");
  }

  /**
   * Construct the Stop Command with the Version, Tc Config file and server name
   *
   * @return String[] representing the start command and its parameters
   */
  private List<String> stopTsaCommand(ServerSymbolicName serverSymbolicName, File installLocation) {
    List<String> options = new ArrayList<>();
    options.add(getStopCmd(installLocation));

    String symbolicName = serverSymbolicName.getSymbolicName();
    if (isValidHost(symbolicName) || isValidIPv4(symbolicName) || isValidIPv6(symbolicName) || symbolicName.isEmpty()) {
      // add -n if applicable
      options.add("-n");
      options.add(symbolicName);
    }

    return options;
  }

  private String getStopCmd(File installLocation) {
    String execPath = "server" + separator + "bin" + separator + "stop-tc-server" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + separator + terracottaInstallationRoot() + separator + execPath;
    }
    throw new IllegalStateException("Can not define Terracotta server Stop Command for distribution: " + distribution);
  }

  @Override
  public void configure(String clusterName, File location, String licensePath, Topology topology, Map<ServerSymbolicName, Integer> proxyTsaPorts, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv, boolean verbose) {
    logger.info("There is no licensing step in 4.x");
  }

  @Override
  public ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    throw new UnsupportedOperationException("4.x does not have a cluster tool");
  }

  @Override
  public ConfigToolExecutionResult invokeConfigTool(File installLocation, TerracottaCommandLineEnvironment env, String... arguments) {
    throw new UnsupportedOperationException("4.x does not support config tool");
  }

  /**
   * Construct the Start Command with the Version, Tc Config file and server name
   *
   * @return List of Strings representing the start command and its parameters
   */
  private List<String> createTsaCommand(ServerSymbolicName serverSymbolicName,
                                        UUID serverId,
                                        ConfigurationManager configurationManager,
                                        Map<ServerSymbolicName, Integer> proxiedPorts,
                                        File installLocation,
                                        List<String> startUpArgs) {
    List<String> options = new ArrayList<>();
    // start command
    options.add(getStartCmd(installLocation));

    String symbolicName = serverSymbolicName.getSymbolicName();
    if (isValidHost(symbolicName) || isValidIPv4(symbolicName) || isValidIPv6(symbolicName) || symbolicName.isEmpty()) {
      // add -n if applicable
      options.add("-n");
      options.add(symbolicName);
    }

    TcConfigManager tcConfigProvider = (TcConfigManager) configurationManager;
    TcConfig tcConfig = TcConfig.copy(tcConfigProvider.findTcConfig(serverId));
    tcConfigProvider.setUpInstallation(tcConfig, serverSymbolicName, serverId, proxiedPorts, installLocation, null);
    // add -f if applicable
    if (tcConfig.getPath() != null) {
      //workaround to have unique platform restart directory for active & passives
      //TODO this can  be removed when platform persistent has server name in the path
      try {
        String modifiedTcConfigPath = tcConfig.getPath()
            .substring(0, tcConfig.getPath()
                .length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
        String modifiedConfig = FileUtils.readFileToString(new File(tcConfig.getPath())).
            replaceAll(Pattern.quote("${restart-data}"), "restart-data/" + serverSymbolicName).
            replaceAll(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
        FileUtils.write(new File(modifiedTcConfigPath), modifiedConfig);
        options.add("-f");
        options.add(modifiedTcConfigPath);
      } catch (IOException ioe) {
        throw new RuntimeException("Error when modifying tc config", ioe);
      }
    }

    options.addAll(startUpArgs);
    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.debug("TSA create command = {}", sb.toString());

    return options;
  }

  private String getStartCmd(File installLocation) {
    String execPath = "server" + separator + "bin" + separator + "start-tc-server" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + separator + terracottaInstallationRoot() + separator + execPath;
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }


  @Override
  public TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment tcEnv) {
    throw new UnsupportedOperationException("NOT YET IMPLEMENTED");
  }

  @Override
  public void stopTms(File installLocation, TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    throw new UnsupportedOperationException("NOT YET IMPLEMENTED");
  }

  @Override
  public URI tsaUri(Collection<TerracottaServer> servers, Map<ServerSymbolicName, Integer> proxyTsaPorts) {
    return URI.create(servers
        .stream()
        .map(s -> new HostPort(s.getHostname(), proxyTsaPorts.getOrDefault(s.getServerSymbolicName(), s.getTsaPort())).getHostPort())
        .collect(Collectors.joining(",", "", "")));
  }

  @Override
  public String clientJarsRootFolderName(Distribution distribution) {
    if (distribution.getPackageType() == KIT) {
      return "apis";
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return "common" + separator + "lib";
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public String pluginJarsRootFolderName(Distribution distribution) {
    throw new UnsupportedOperationException("4.x does not support plugins");
  }

  @Override
  public String terracottaInstallationRoot() {
    return "Terracotta";
  }
}
