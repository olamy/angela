package com.terracottatech.qa.angela.common.distribution;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.common.kit.ServerLogOutputStream;
import com.terracottatech.qa.angela.common.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.ClusterToolException;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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
    System.out.println("[Server-side] Jenkins build Id = " + System.getProperty("jenkins.build.id"));

    AtomicInteger pid = new AtomicInteger(-1);
    ServerLogOutputStream serverLogOutputStream = new ServerLogOutputStream(pid);

    ProcessExecutor executor = new ProcessExecutor()
        .command(startCommand(serverSymbolicName, topology, installLocation))
        .directory(installLocation)
        .environment(env)
        .redirectOutput(serverLogOutputStream);
    StartedProcess startedProcess;
    try {
      startedProcess = executor.start();
    } catch (IOException e) {
      throw new RuntimeException("Can not start Terracotta server process " + serverSymbolicName, e);
    }

    return new TerracottaServerInstance.TerracottaServerInstanceProcess(startedProcess, pid, serverLogOutputStream,
        serverLogOutputStream.waitForStartedState(startedProcess));
  }

  @Override
  public TerracottaServerState stop(final ServerSymbolicName serverSymbolicName, final File installLocation, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess) {
    StartedProcess startedProcess = terracottaServerInstanceProcess.getStartedProcess();
    if (startedProcess != null) {
      logger.info("Forcefully destroying L2 process for " + serverSymbolicName);
      try {
        startedProcess.getProcess().destroy();
      } catch (Exception e) {
        logger.info("Could not destroy process, trying with system command.", e);
        systemKill(terracottaServerInstanceProcess.getPid().get());
        terracottaServerInstanceProcess.getPid().set(-1);
      }
    }
    if (terracottaServerInstanceProcess.getPid().get() != -1) {
      try {
        ProcessUtil.destroyForcefullyAndWait(Processes.newPidProcess(terracottaServerInstanceProcess.getPid()
            .get()), 30, TimeUnit.SECONDS);
      } catch (Exception e) {
        logger.info("Could not destroy process, trying with system command.", e);
        systemKill(terracottaServerInstanceProcess.getPid().get());
      }
      terracottaServerInstanceProcess.getPid().set(-1);
    }

    terracottaServerInstanceProcess.getLogs().stop();
    return TerracottaServerState.STOPPED;
  }

  @Override
  public void configureLicense(final String topologyId, final File location, final License license, final TcConfig[] tcConfigs) {
    Map<String, String> env = new HashMap<String, String>();
    List<String> j8Homes = javaLocationResolver.resolveJava8Location();
    if (j8Homes.size() > 0) {
      env.put("JAVA_HOME", j8Homes.get(0));
    }

    String[] commands = configureCommand(topologyId, location, license, tcConfigs);

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
      throw new ClusterToolException("Error when installing the cluster license", processResult.outputString(), processResult.getExitValue());
    }
  }

  private synchronized String[] configureCommand(final String clusterName, final File location, final License licenseConfig, final TcConfig[] tcConfigs) {
    List<String> command = new ArrayList<>();

    StringBuilder sb = null;
    if (distribution.getPackageType() == KIT) {
      sb = new StringBuilder(location.getAbsolutePath() + File.separator + "tools" + File.separator + "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool")
          .append(os.getShellExtension());
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      sb = new StringBuilder(location.getAbsolutePath() + File.separator + "TDB" + File.separator + "TerracottaDB"
                             + File.separator + "tools" + File.separator + "cluster-tool" + File.separator + "bin" + File.separator + "cluster-tool")
          .append(os.getShellExtension());
    }
    command.add(sb.toString());
    command.add("configure");
    command.add("-n");
    command.add(clusterName);

    if (distribution.getPackageType() == KIT) {
      File tmpLicenseFile = new File(location + File.separator + "license.xml");
      licenseConfig.WriteToFile(tmpLicenseFile);
      command.add("-l");
      command.add(tmpLicenseFile.getAbsolutePath());
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      //No explicit license for cluster tool on sag install as it uses default license put by sag installer
      //on cluster-tool/conf folder
    }

    File tmpConfigDir = new File(location + File.separator + "tc-configs");
    tmpConfigDir.mkdir();


    for (TcConfig tcConfig : tcConfigs) {
      tcConfig.writeTcConfigFile(tmpConfigDir);
      command.add(tcConfig.getPath());
    }

    return command.toArray(new String[command.size()]);
  }

  private void systemKill(Integer pid) {
    ProcessExecutor executor = new ProcessExecutor()
        .command(getKillCmd(pid));

    try {
      executor.start().getFuture().get();
    } catch (Exception e) {
      throw new RuntimeException("Could not kill Terracotta server instance", e);
    }
  }

  private String[] getKillCmd(Integer pid) {
    if (os.isPosix()) {
      return new String[] { "kill", "" + pid };
    }
    if (os.isWindows()) {
      return new String[] { "Taskkill", "/PID", "" + pid, "/F" };
    }
    throw new RuntimeException("Can not define Kill process Command for Os" + os.getPlatformName());
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
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "TDB" + File.separator + "TerracottaDB"
                                             + File.separator + "server" + File.separator + "bin" + File.separator + "start-tc-server")
            .append(os.getShellExtension());
        return sb.toString();
      }
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }
}
