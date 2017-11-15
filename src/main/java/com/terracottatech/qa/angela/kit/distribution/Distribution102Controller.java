package com.terracottatech.qa.angela.kit.distribution;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.JavaLocationResolver;
import com.terracottatech.qa.angela.OS;
import com.terracottatech.qa.angela.kit.ServerLogOutputStream;
import com.terracottatech.qa.angela.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.kit.TerracottaServerState;
import com.terracottatech.qa.angela.tcconfig.TcConfig;
import com.terracottatech.qa.angela.topology.Topology;
import com.terracottatech.qa.angela.topology.Version;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static com.terracottatech.qa.angela.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.topology.PackageType.SAG_INSTALLER;

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
  public TerracottaServerInstance.TerracottaServerInstanceProcess start(final String serverSymbolicName, final File installLocation) {
    Map<String, String> env = new HashMap<String, String>();
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
  public TerracottaServerState stop(final String serverSymbolicName, final File installLocation, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess) {
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
  private String[] startCommand(final String serverSymbolicName, final Topology topology, final File installLocation) {
    List<String> options = new LinkedList<String>();
    // start command
    options.add(getStartCmd(installLocation));

    // add -n if applicable
    if (!(serverSymbolicName.contains(":") || (serverSymbolicName.isEmpty()))) {
      options.add("-n");
      options.add(serverSymbolicName);
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
                                                     .length() - 4) + "-" + serverSymbolicName + ".xml";
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
