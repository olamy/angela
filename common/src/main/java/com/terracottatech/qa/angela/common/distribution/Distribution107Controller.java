package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.TerracottaServerInstance.TerracottaServerInstanceProcess;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.ExternalLoggers;
import com.terracottatech.qa.angela.common.util.HostPort;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.ProcessUtil;
import com.terracottatech.qa.angela.common.util.TriggeringOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.terracottatech.qa.angela.common.AngelaProperties.TMS_FULL_LOGGING;
import static com.terracottatech.qa.angela.common.AngelaProperties.TSA_FULL_LOGGING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_IN_DIAGNOSTIC_MODE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static com.terracottatech.qa.angela.common.util.ExternalLoggers.tsaLogger;
import static java.io.File.separator;
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.compile;

public class Distribution107Controller extends DistributionController {
  private final static Logger LOGGER = LoggerFactory.getLogger(Distribution107Controller.class);
  private final boolean tsaFullLogging = Boolean.parseBoolean(TSA_FULL_LOGGING.getValue());
  private final boolean tmsFullLogging = Boolean.parseBoolean(TMS_FULL_LOGGING.getValue());

  public Distribution107Controller(Distribution distribution) {
    super(distribution);
  }

  @Override
  public TerracottaServerInstanceProcess createTsa(TerracottaServer terracottaServer, File installLocation,
                                                   Topology topology, Map<String, Integer> proxiedPorts,
                                                   TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs) {
    Map<String, String> env = buildEnv(tcEnv);
    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    AtomicInteger javaPid = new AtomicInteger(-1);

    TriggeringOutputStream serverLogOutputStream = TriggeringOutputStream
        .triggerOn(
            compile("^.*\\QStarted the server in diagnostic mode\\E.*$"),
            mr -> stateRef.set(STARTED_IN_DIAGNOSTIC_MODE))
        .andTriggerOn(
            compile("^.*\\QTerracotta Server instance has started up as ACTIVE\\E.*$"),
            mr -> stateRef.set(STARTED_AS_ACTIVE))
        .andTriggerOn(
            compile("^.*\\QMoved to State[ PASSIVE-STANDBY ]\\E.*$"),
            mr -> stateRef.set(STARTED_AS_PASSIVE))
        .andTriggerOn(
            compile("^.*\\QStopping server\\E.*$"),
            mr -> stateRef.set(STOPPED))
        .andTriggerOn(
            compile("^.*PID is (\\d+).*$"),
            mr -> {
              javaPid.set(parseInt(mr.group(1)));
              stateRef.compareAndSet(STOPPED, STARTING);
            })
        .andTriggerOn(
            tsaFullLogging ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"),
            mr -> tsaLogger.info("[{}] {}", terracottaServer.getServerSymbolicName().getSymbolicName(), mr.group()));

    WatchedProcess<TerracottaServerState> watchedProcess = new WatchedProcess<>(
        new ProcessExecutor()
            .command(createTsaCommand(terracottaServer, installLocation, startUpArgs))
            .directory(installLocation)
            .environment(env)
            .redirectError(System.err)
            .redirectOutput(serverLogOutputStream),
        stateRef,
        STOPPED);

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
  public TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment tcEnv) {
    Map<String, String> env = buildEnv(tcEnv);

    AtomicReference<TerracottaManagementServerState> stateRef = new AtomicReference<>(TerracottaManagementServerState.STOPPED);
    AtomicInteger javaPid = new AtomicInteger(-1);

    TriggeringOutputStream outputStream = TriggeringOutputStream
        .triggerOn(
            compile("^.*\\Qstarted on port\\E.*$"),
            mr -> stateRef.set(TerracottaManagementServerState.STARTED)
        ).andTriggerOn(
            compile("^.*\\QStarting TmsApplication\\E.*with PID (\\d+).*$"),
            mr -> javaPid.set(parseInt(mr.group(1)))
        ).andTriggerOn(
            tmsFullLogging ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"),
            mr -> ExternalLoggers.tmsLogger.info(mr.group())
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
    LOGGER.debug("Destroying TMS process");
    for (Number pid : terracottaServerInstanceProcess.getPids()) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(pid.intValue());
      } catch (Exception e) {
        LOGGER.error("Could not destroy TMS process {}", pid, e);
      }
    }
  }

  @Override
  public void stopTsa(ServerSymbolicName serverSymbolicName, File location, TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    LOGGER.debug("Destroying TC server process for {}", serverSymbolicName);
    for (Number pid : terracottaServerInstanceProcess.getPids()) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(pid.intValue());
      } catch (Exception e) {
        LOGGER.error("Could not destroy TC server process with PID '{}'", pid, e);
      }
    }
  }

  @Override
  public void configure(String clusterName, File location, String licensePath, Topology topology, Map<ServerSymbolicName,
      Integer> proxyTsaPorts, SecurityRootDirectory srd, TerracottaCommandLineEnvironment tcEnv, boolean verbose) {
    TerracottaServer server = topology.getServers().get(0);
    invokeConfigTool(location, tcEnv, "activate", "-n", clusterName, "-l", licensePath, "-s", server.getHostPort());
  }

  @Override
  public ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment env, String... arguments) {
    throw new UnsupportedOperationException("Dynamic config don't use cluster-tool");
  }

  @Override
  public ConfigToolExecutionResult invokeConfigTool(File installLocation, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    List<String> command = new ArrayList<>();
    command.add(installLocation
        + separator + "tools"
        + separator + "config-tool"
        + separator + "bin"
        + separator + "config-tool" + OS.INSTANCE.getShellExtension());
    command.addAll(Arrays.asList(arguments));
    LOGGER.info("Invoking config tool with args: {}", Arrays.asList(arguments));

    try {
      ProcessResult processResult = new ProcessExecutor(command)
          .directory(installLocation)
          .environment(buildEnv(tcEnv))
          .redirectErrorStream(true)
          .readOutput(true)
          .execute();
      LOGGER.info("Invoke config tool output: {}{}", System.lineSeparator(), processResult.outputString());
      return new ConfigToolExecutionResult(processResult.getExitValue(), processResult.getOutput().getLines());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  private List<String> createTsaCommand(TerracottaServer terracottaServer, File installLocation, List<String> startUpArgs) {
    List<String> command = new ArrayList<>();
    command.add(getTsaCreateExecutable(installLocation));

    if (startUpArgs != null && !startUpArgs.isEmpty()) {
      command.addAll(startUpArgs);
    } else {
      List<String> dynamicArguments = addOptions(terracottaServer);
      command.addAll(dynamicArguments);
    }

    LOGGER.debug(" Create TSA command = {}", command);
    return command;
  }

  private List<String> addOptions(TerracottaServer server) {
    List<String> options = new ArrayList<>();

    if (server.getConfigFile() != null) {
      options.add("-f");
      options.add(server.getConfigFile());
    } else {
      // Add server name only if config file option wasn't provided
      options.add("-n");
      options.add(server.getServerSymbolicName().getSymbolicName());
    }

    // Add hostname
    options.add("-s");
    options.add(server.getHostname());

    if (server.getTsaPort() != 0) {
      options.add("-p");
      options.add(String.valueOf(server.getTsaPort()));
    }

    if (server.getTsaGroupPort() != 0) {
      options.add("-g");
      options.add(String.valueOf(server.getTsaGroupPort()));
    }

    if (server.getBindAddress() != null) {
      options.add("-a");
      options.add(server.getBindAddress());
    }

    if (server.getGroupBindAddress() != null) {
      options.add("-A");
      options.add(server.getGroupBindAddress());
    }

    if (server.getConfigRepo() != null) {
      options.add("-r");
      options.add(server.getConfigRepo());
    }

    if (server.getMetaData() != null) {
      options.add("-m");
      options.add(server.getMetaData());
    }

    if (server.getDataDir() != null) {
      options.add("-d");
      options.add(server.getDataDir());
    }

    if (server.getOffheap() != null) {
      options.add("-o");
      options.add(server.getOffheap());
    }

    if (server.getLogs() != null) {
      options.add("-L");
      options.add(server.getLogs());
    }

    if (server.getFailoverPriority() != null) {
      options.add("-y");
      options.add(server.getFailoverPriority());
    }

    LOGGER.info("Server startup options: {}", options);
    return options;
  }

  private String getTsaCreateExecutable(File installLocation) {
    String execPath = "server" + separator + "bin" + separator + "start-node" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + separator + "TerracottaDB" + separator + execPath;
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }

  private List<String> startTmsCommand(File installLocation) {
    List<String> command = new ArrayList<>();
    command.add(getStartTmsExecutable(installLocation));
    LOGGER.debug(" Start TMS command = {}", command);
    return command;
  }

  private String getStartTmsExecutable(File installLocation) {
    String execPath = "tools" + separator + "management" + separator + "bin" + separator + "start" + OS.INSTANCE.getShellExtension();
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + separator + execPath;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + separator + "TerracottaDB" + separator + execPath;
    }
    throw new IllegalStateException("Can not define TMS Start Command for distribution: " + distribution);
  }
}