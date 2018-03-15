package com.terracottatech.qa.angela.common.distribution;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.ServerLogOutputStream;
import com.terracottatech.qa.angela.common.util.TerracottaManagementServerLogOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
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

public class Distribution43Controller extends DistributionController {

  private final static Logger logger = LoggerFactory.getLogger(Distribution43Controller.class);
  public static final long L2_STOP_TIMEOUT = 60000L;

  public Distribution43Controller(final Distribution distribution, final Topology topology) {
    super(distribution, topology);
  }

  @Override
  public TerracottaServerInstance.TerracottaServerInstanceProcess start(final ServerSymbolicName serverSymbolicName, final File installLocation) {
    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    ServerLogOutputStream serverLogOutputStream = new ServerLogOutputStream(serverSymbolicName, stateRef);

    ProcessExecutor executor = new ProcessExecutor()
        .command(startCommand(serverSymbolicName, topology, installLocation))
        .directory(installLocation)
        .environment(buildEnv())
        .redirectError(System.err)
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
    return new TerracottaServerInstance.TerracottaServerInstanceProcess(stateRef, wrapperPid);
  }

  @Override
  public void stop(final ServerSymbolicName serverSymbolicName, final File installLocation, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess) {
    ProcessExecutor executor = new ProcessExecutor()
        .command(stopCommand(serverSymbolicName, topology, installLocation))
        .directory(installLocation)
        .environment(buildEnv())
        .redirectError(System.err)
        .redirectOutput(System.out);  // TODO

    try {
      logger.info("Calling stop command for server {}", serverSymbolicName);
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

    int[] pids = terracottaServerInstanceProcess.getPids();
    logger.info("Destroying L2 process for {}", serverSymbolicName);
    for (int pid : pids) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);

        for (int i = 0; i < 100; i++) {
          if (terracottaServerInstanceProcess.getState() == STOPPED) {
            return;
          }
          Thread.sleep(100);
        }
      } catch (Exception e) {
        logger.warn("Could not destroy process {}", pid, e);
      }
    }

    throw new RuntimeException("Terracotta server [" + serverSymbolicName + "] could not be stopped.");
  }

  /**
   * Construct the Stop Command with the Version, Tc Config file and server name
   *
   * @param serverSymbolicName
   * @param topology
   * @param installLocation
   * @return String[] representing the start command and its parameters
   */
  private String[] stopCommand(final ServerSymbolicName serverSymbolicName, final Topology topology, final File installLocation) {
    List<String> options = new LinkedList<String>();
    options.add(getStopCmd(installLocation));

    // add -n if applicable
    if (!(serverSymbolicName.getSymbolicName().contains(":") || (serverSymbolicName.getSymbolicName().isEmpty()))) {
      options.add("-n");
      options.add(serverSymbolicName.getSymbolicName());
    }

    // add -f if applicable
    TcConfig tcConfig = topology.getTcConfig(serverSymbolicName);
    if (tcConfig.getPath() != null) {
      options.add("-f");
      options.add(tcConfig.getPath());
    }

    return options.toArray(new String[options.size()]);
  }

  private String getStopCmd(final File installLocation) {
    Version version = distribution.getVersion();

    if (version.getMajor() == 4 || version.getMajor() == 5 || version.getMajor() == 10) {
      if (distribution.getPackageType() == KIT) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "server" + File.separator + "bin" + File.separator + "stop-tc-server")
            .append(os.getShellExtension());
        return sb.toString();
//      } else if (distribution.getPackageType() == SAG_INSTALLER) {
//        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
//                                             + File.separator + "server" + File.separator + "bin" + File.separator + "stop-tc-server")
//            .append(os.getShellExtension());
//        return sb.toString();
      }
    }
    throw new IllegalStateException("Can not define Terracotta server Stop Command for distribution: " + distribution);

  }


  @Override
  public void configureLicense(final InstanceId instanceId, final File location, final License license, final TcConfig[] tcConfigs, final SecurityRootDirectory securityRootDirectory) {
    logger.info("There is licensing for 4.x");
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
            replaceAll(Pattern.quote("${restart-data}"), String.valueOf("restart-data/" + serverSymbolicName)).
            replaceAll(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
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


  @Override
  public TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(final File installLocation) {
    Map<String, String> env = buildEnv();

    AtomicReference<TerracottaManagementServerState> stateRef = new AtomicReference<>(TerracottaManagementServerState.STOPPED);
    ServerSymbolicName tmsSymbolicName = new ServerSymbolicName("TMS");
    TerracottaManagementServerLogOutputStream terracottaManagementServerLogOutputStream = new TerracottaManagementServerLogOutputStream(stateRef);

    ProcessExecutor executor = new ProcessExecutor()
        .command(startTMSCommand(installLocation))
        .directory(installLocation)
        .environment(env)
        .redirectError(System.err)
        .redirectOutput(terracottaManagementServerLogOutputStream);
    StartedProcess startedProcess;
    try {
      startedProcess = executor.start();
      // spawn a thread that resets stateRef to STOPPED when the TC server process dies
      Thread processWatcher = new Thread(() -> {
        try {
          startedProcess.getFuture().get();
          stateRef.set(TerracottaManagementServerState.STOPPED);
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      });
      processWatcher.setDaemon(true);
      processWatcher.start();
    } catch (IOException e) {
      throw new RuntimeException("Can not start Terracotta server process " + tmsSymbolicName, e);
    }

    terracottaManagementServerLogOutputStream.waitForStartedState(startedProcess);

    int wrapperPid = PidUtil.getPid(startedProcess.getProcess());
    int javaProcessPid = terracottaManagementServerLogOutputStream.getPid();
    return new TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess(stateRef, wrapperPid, javaProcessPid);
  }

  @Override
  public void stopTms(final File installLocation, final TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess) {
    int[] pids = terracottaServerInstanceProcess.getPids();
    logger.info("Destroying TMS process");
    for (int pid : pids) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
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
            .append(os.getShellExtension());
        return sb.toString();
      } else if (distribution.getPackageType() == SAG_INSTALLER) {
        StringBuilder sb = new StringBuilder(installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
                                             + File.separator + "tools" + File.separator + "management" + File.separator + "bin" + File.separator + "start")
            .append(os.getShellExtension());
        return sb.toString();
      }
    }
    throw new IllegalStateException("Can not define TMS Start Command for distribution: " + distribution);
  }
}
