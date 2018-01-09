package com.terracottatech.qa.angela.common.distribution;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.common.ClusterToolException;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.ServerLogOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution102Controller extends DistributionController {

  private final static Logger logger = LoggerFactory.getLogger(Distribution102Controller.class);

  private final JavaLocationResolver javaLocationResolver = new JavaLocationResolver();
  private final OS os = new OS();


  public Distribution102Controller(final Distribution distribution, final Topology topology) {
    super(distribution, topology);
  }

  @Override
  public TerracottaServerInstance.TerracottaServerInstanceProcess start(final ServerSymbolicName serverSymbolicName, final File installLocation) {
    Map<String, String> env = new HashMap<>();
    List<String> j8Homes = javaLocationResolver.resolveJava8Location();
    if (j8Homes.size() > 0) {
      env.put("JAVA_HOME", j8Homes.get(0));
    }

    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    ServerLogOutputStream serverLogOutputStream = new ServerLogOutputStream(serverSymbolicName, stateRef);

    ProcessExecutor executor = new ProcessExecutor()
        .command(startCommand(serverSymbolicName, topology, installLocation))
        .directory(installLocation)
        .environment(env)
        .redirectOutput(serverLogOutputStream);
    StartedProcess startedProcess;
    try {
      startedProcess = executor.start();
      // spawn a thread that resets stateRef to STOPPED when the TC server process dies
      Thread processWatcher = new Thread(() -> {
        try {
          startedProcess.getFuture().get();
          stateRef.set(STOPPED);
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      });
      processWatcher.setDaemon(true);
      processWatcher.start();
    } catch (IOException e) {
      throw new RuntimeException("Can not start Terracotta server process " + serverSymbolicName, e);
    }


    serverLogOutputStream.waitForStartedState(startedProcess);

    int wrapperPid = PidUtil.getPid(startedProcess.getProcess());
    int javaProcessPid = serverLogOutputStream.getPid();
    return new TerracottaServerInstance.TerracottaServerInstanceProcess(stateRef, wrapperPid, javaProcessPid);
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
  public void configureLicense(final InstanceId instanceId, final File location, final License license, final TcConfig[] tcConfigs) {
    Map<String, String> env = new HashMap<String, String>();
    List<String> j8Homes = javaLocationResolver.resolveJava8Location();
    if (j8Homes.size() > 0) {
      env.put("JAVA_HOME", j8Homes.get(0));
    }

    String[] commands = configureCommand(instanceId, location, license, tcConfigs);

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

  private synchronized String[] configureCommand(final InstanceId instanceId, final File location, final License licenseConfig, final TcConfig[] tcConfigs) {
    List<String> command = new ArrayList<>();

    StringBuilder sb = null;
    if (distribution.getPackageType() == KIT) {
      sb = new StringBuilder(location.getAbsolutePath() + File.separator + "tools" + File.separator + "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool")
          .append(os.getShellExtension());
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      sb = new StringBuilder(location.getAbsolutePath() + File.separator + "TerracottaDB"
                             + File.separator + "tools" + File.separator + "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool")
          .append(os.getShellExtension());
    }
    command.add(sb.toString());
    command.add("configure");
    command.add("-n");
    command.add(instanceId.toString());

    File tmpLicenseFile = new File(location + File.separator + "license.xml");
    licenseConfig.WriteToFile(tmpLicenseFile);
    command.add("-l");
    command.add(tmpLicenseFile.getAbsolutePath());

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
   * @param topology
   * @return String[] representing the start command and its parameters
   */
  private String[] startCommand(final ServerSymbolicName serverSymbolicName, final Topology topology, final File installLocation) {
    List<String> options = new ArrayList<String>();
    // start command
    options.add(getStartCmd(installLocation));

    // add -n if applicable
    if (!(serverSymbolicName.getSymbolicName().contains(":") || (serverSymbolicName.getSymbolicName().isEmpty()))) {
      options.add("-n");
      options.add(serverSymbolicName.getSymbolicName());
    }

    // add -f if applicable
    TcConfig tcConfig = topology.getTcConfig(serverSymbolicName);
    if (tcConfig.getPath() != null) {
      //workaround to have unique platform restart directory for active & passives
      //TODO this can  be removed when platform persistent has server name in the path
      String modifiedTcConfigPath = null;
      try {
        modifiedTcConfigPath = tcConfig.getPath()
                                   .substring(0, tcConfig.getPath()
                                                     .length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
        String modifiedConfig = FileUtils.readFileToString(new File(tcConfig.getPath())).
            replaceAll(Pattern.quote("${restart-data}"), String.valueOf("restart-data/" + serverSymbolicName));
        FileUtils.write(new File(modifiedTcConfigPath), modifiedConfig);
      } catch (IOException e) {
        throw new RuntimeException("Error when modifying tc config", e);
      }
      options.add("-f");
      options.add(modifiedTcConfigPath);
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
            .append(os.getShellExtension());
        return sb.toString();
      } else if (distribution.getPackageType() == SAG_INSTALLER) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
                                             + File.separator + "server" + File.separator + "bin" + File.separator + "start-tc-server")
            .append(os.getShellExtension());
        return sb.toString();
      }
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }
}
