package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.common.ClusterToolException;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.TriggeringOutputStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution102Controller extends DistributionController {

  private final boolean tsaFullLogs = Boolean.getBoolean("angela.tsa.log.full");
  private final boolean tmsFullLogs = Boolean.getBoolean("angela.tms.log.full");

  private final static Logger logger = LoggerFactory.getLogger(Distribution102Controller.class);

  public Distribution102Controller(final Distribution distribution, final Topology topology) {
    super(distribution, topology);
  }

  @Override
  public TerracottaServerInstance.TerracottaServerInstanceProcess create(final ServerSymbolicName serverSymbolicName, final File installLocation, final TcConfig tcConfig) {
    Map<String, String> env = buildEnv();

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
        tsaFullLogs ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"), mr -> System.out.println("[" + serverSymbolicName.getSymbolicName() + "] " + mr.group())
    );
    WatchedProcess watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(startCommand(serverSymbolicName, tcConfig, installLocation))
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
      throw new RuntimeException("Terracotta server process died in its infancy : " + serverSymbolicName);
    }
    return new TerracottaServerInstance.TerracottaServerInstanceProcess(stateRef, watchedProcess.getPid(), javaPid);
  }

  @Override
  public void stop(final ServerSymbolicName serverSymbolicName, final File installLocation, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess) {
    int[] pids = terracottaServerInstanceProcess.getPids();
    logger.info("Destroying L2 process for " + serverSymbolicName);
    for (int pid : pids) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
      } catch (Exception e) {
        logger.warn("Could not destroy process {}", pid, e);
      }
    }
  }

  @Override
  public ClusterToolExecutionResult invokeClusterTool(File installLocation, String... arguments) {
    List<String> args = new ArrayList<>();
    args.add(installLocation
        + File.separator + "tools"
        + File.separator + "cluster-tool"
        + File.separator + "bin"
        + File.separator + "cluster-tool" + OS.INSTANCE.getShellExtension());

    args.addAll(Arrays.asList(arguments));

    try {
      ProcessBuilder builder = new ProcessBuilder(args);
      builder.environment().putAll(buildEnv());
      builder.inheritIO();
      builder.redirectErrorStream(true);
      File out_log = File.createTempFile("cluster-tool-output", ".tmp");
      builder.redirectOutput(ProcessBuilder.Redirect.appendTo(out_log));

      Process process = builder.start();
      int result = process.waitFor();

      List<String> stdout = Files.lines(out_log.toPath()).collect(toList());
      out_log.delete();
      return new ClusterToolExecutionResult(result, stdout);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void configureLicense(String clusterName, final File location, String licensePath, final TcConfig[] tcConfigs, final SecurityRootDirectory securityRootDirectory) {
    Map<String, String> env = buildEnv();

    String[] commands = configureCommand(location, licensePath, tcConfigs, clusterName, securityRootDirectory);

    ProcessExecutor executor = new ProcessExecutor()
        .command(commands).directory(location).environment(env);

    ProcessResult processResult;
    try {
      StartedProcess startedProcess = executor.readOutput(true).start();
      processResult = startedProcess.getFuture().get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    if (processResult.getExitValue() != 0) {
      throw new ClusterToolException("Error when installing the cluster license", processResult.outputString(), processResult
          .getExitValue());
    }
  }

  private synchronized String[] configureCommand(final File location, String licensePath, final TcConfig[] tcConfigs, String clusterName, final SecurityRootDirectory securityRootDirectory) {
    List<String> command = new ArrayList<>();

    StringBuilder sb = null;
    if (distribution.getPackageType() == KIT) {
      sb = new StringBuilder(location.getAbsolutePath() + File.separator + "tools" + File.separator + "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool")
          .append(OS.INSTANCE.getShellExtension());
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      sb = new StringBuilder(location.getAbsolutePath() + File.separator + "TerracottaDB"
                             + File.separator + "tools" + File.separator + "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool")
          .append(OS.INSTANCE.getShellExtension());
    }
    command.add(sb.toString());
    if (securityRootDirectory != null) {
      Path securityRootDirectoryPath = location.toPath().resolve("cluster-tool-security").resolve("security-root-directory");
      securityRootDirectory.createSecurityRootDirectory(securityRootDirectoryPath);
      logger.info("Using SecurityRootDirectory " + securityRootDirectoryPath);
      command.add("-srd");
      command.add(securityRootDirectoryPath.toString());
    }
    command.add("-v");
    command.add("configure");
    command.add("-n");
    command.add(clusterName);
    command.add("-l");
    command.add(licensePath);

    File tmpConfigDir = new File(location + File.separator + "tc-configs");
    tmpConfigDir.mkdir();

    for (TcConfig tcConfig : tcConfigs) {
      tcConfig.writeTcConfigFile(tmpConfigDir);
      command.add(tcConfig.getPath());
    }

    return command.toArray(new String[command.size()]);
  }

  /**
   * Construct the Start Command with the Version, Tc Config file and server name
   *
   * @param serverSymbolicName
   * @param tcConfig
   * @return String[] representing the start command and its parameters
   */
  private String[] startCommand(final ServerSymbolicName serverSymbolicName, final TcConfig tcConfig, final File installLocation) {
    List<String> options = new ArrayList<String>();
    // start command
    options.add(getStartCmd(installLocation));

    // add -n if applicable
    if (!(serverSymbolicName.getSymbolicName().contains(":") || (serverSymbolicName.getSymbolicName().isEmpty()))) {
      options.add("-n");
      options.add(serverSymbolicName.getSymbolicName());
    }

    // add -f if applicable
    if (tcConfig.getPath() != null) {
      options.add("-f");
      options.add(tcConfig.getPath());
    }

    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.info(" Start command = {}", sb.toString());

    return options.toArray(new String[options.size()]);
  }

  private String getStartCmd(File installLocation) {
    Version version = distribution.getVersion();

    if (version.getMajor() == 4 || version.getMajor() == 5 || version.getMajor() == 10) {
      if (distribution.getPackageType() == KIT) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "server" + File.separator + "bin" + File.separator + "start-tc-server")
            .append(OS.INSTANCE.getShellExtension());
        return sb.toString();
      } else if (distribution.getPackageType() == SAG_INSTALLER) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
                                             + File.separator + "server" + File.separator + "bin" + File.separator + "start-tc-server")
            .append(OS.INSTANCE.getShellExtension());
        return sb.toString();
      }
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }


  @Override
  public TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(final File installLocation) {
    Map<String, String> env = buildEnv();

    AtomicReference<TerracottaManagementServerState> stateRef = new AtomicReference<>(TerracottaManagementServerState.STOPPED);
    AtomicInteger javaPid = new AtomicInteger(-1);

    TriggeringOutputStream outputStream = TriggeringOutputStream.triggerOn(
        compile("^.*\\Qstarted on port\\E.*$"), mr -> stateRef.set(TerracottaManagementServerState.STARTED)
    ).andTriggerOn(
        compile("^.*\\QStarting TmsApplication\\E.*with PID (\\d+).*$"), mr -> javaPid.set(parseInt(mr.group(1)))
    ).andTriggerOn(
        tmsFullLogs ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"), mr -> System.out.println("[TMS] " + mr.group())
    );
    WatchedProcess watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(startTMSCommand(installLocation))
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
  public void stopTms(final File installLocation, final TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess) {
    Number[] pids = terracottaServerInstanceProcess.getPids();
    logger.info("Destroying TMS process");
    for (Number pid : pids) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid.intValue()), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
      } catch (Exception e) {
        logger.warn("Could not destroy process {}", pid, e);
      }
    }
  }

  /**
   * Construct the Start Command with the Version
   *
   * @return String[] representing the start command and its parameters
   */
  private String[] startTMSCommand(final File installLocation) {
    List<String> options = new ArrayList<String>();
    // start command
    options.add(getStartTMSCmd(installLocation));

    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.info(" Start command = {}", sb.toString());

    return options.toArray(new String[options.size()]);
  }

  private String getStartTMSCmd(File installLocation) {
    Version version = distribution.getVersion();

    if (version.getMajor() == 4 || version.getMajor() == 5 || version.getMajor() == 10) {
      if (distribution.getPackageType() == KIT) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "tools" + File.separator + "management" + File.separator + "bin" + File.separator + "start")
            .append(OS.INSTANCE.getShellExtension());
        return sb.toString();
      } else if (distribution.getPackageType() == SAG_INSTALLER) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
            + File.separator + "tools" + File.separator + "management" + File.separator + "bin" + File.separator + "start")
            .append(OS.INSTANCE.getShellExtension());
        return sb.toString();
      }
    }
    throw new IllegalStateException("Can not define TMS Start Command for distribution: " + distribution);
  }
}
