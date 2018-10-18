package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.TriggeringOutputStream;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static java.util.regex.Pattern.compile;

/**
 * @author Aurelien Broszniowski
 */
public class Distribution43Controller extends DistributionController {

  private final boolean tsaFullLogs = Boolean.getBoolean("angela.tsa.log.full");

  private final static Logger logger = LoggerFactory.getLogger(Distribution43Controller.class);

  Distribution43Controller(Distribution distribution) {
    super(distribution);
    Version version = distribution.getVersion();
    if (version.getMajor() != 4) {
      throw new IllegalStateException(getClass().getSimpleName() + " cannot work with distribution version " + version);
    }
  }

  @Override
  public TerracottaServerInstance.TerracottaServerInstanceProcess createTsa(ServerSymbolicName serverSymbolicName, File installLocation, TcConfig tcConfig,
                                                                            TerracottaCommandLineEnvironment tcEnv) {
    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    AtomicReference<TerracottaServerState> tempStateRef = new AtomicReference<>(STOPPED);

    TriggeringOutputStream serverLogOutputStream = TriggeringOutputStream.triggerOn(
        compile("^.*\\QTerracotta Server instance has started up as ACTIVE\\E.*$"), mr -> tempStateRef.set(STARTED_AS_ACTIVE)
    ).andTriggerOn(
        compile("^.*\\QMoved to State[ PASSIVE-STANDBY ]\\E.*$"), mr -> tempStateRef.set(STARTED_AS_PASSIVE)
    ).andTriggerOn(
        compile("^.*\\QManagement server started\\E.*$"), mr -> stateRef.set(tempStateRef.get())
    ).andTriggerOn(
        tsaFullLogs ? compile("^.*$") : compile("^.*(WARN|ERROR).*$"), mr -> System.out.println("[" + serverSymbolicName
            .getSymbolicName() + "] " + mr.group())
    );

    WatchedProcess<TerracottaServerState> watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(createTsaCommand(serverSymbolicName, tcConfig, installLocation))
        .directory(installLocation)
        .environment(buildEnv(tcEnv))
        .redirectError(System.err)
        .redirectOutput(serverLogOutputStream), stateRef, STOPPED);

    int wrapperPid = watchedProcess.getPid();
    return new TerracottaServerInstance.TerracottaServerInstanceProcess(stateRef, wrapperPid);
  }

  @Override
  public void stopTsa(ServerSymbolicName serverSymbolicName, TcConfig tcConfig, File installLocation, TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    ProcessExecutor executor = new ProcessExecutor()
        .command(stopTsaCommand(serverSymbolicName, tcConfig, installLocation))
        .directory(installLocation)
        .environment(buildEnv(tcEnv))
        .redirectError(System.err)
        .redirectOutput(System.out);  // TODO

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
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid.intValue()), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);

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
   * @return String[] representing the start command and its parameters
   */
  private List<String> stopTsaCommand(ServerSymbolicName serverSymbolicName, TcConfig tcConfig, File installLocation) {
    List<String> options = new ArrayList<>();
    options.add(getStopCmd(installLocation));

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

    return options;
  }

  private String getStopCmd(File installLocation) {
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + File.separator + "server" + File.separator + "bin" +
          File.separator + "stop-tc-server" + OS.INSTANCE.getShellExtension();
    }
    throw new IllegalStateException("Can not define Terracotta server Stop Command for distribution: " + distribution);
  }

  @Override
  public void configureTsaLicense(String clusterName, File location, String licensePath, List<TcConfig> tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv, boolean verbose) {
    logger.info("There is no licensing step in 4.x");
  }

  @Override
  public ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    throw new UnsupportedOperationException("4.x does not have a cluster tool");
  }

  /**
   * Construct the Start Command with the Version, Tc Config file and server name
   *
   * @return List of Strings representing the start command and its parameters
   */
  private List<String> createTsaCommand(ServerSymbolicName serverSymbolicName, TcConfig tcConfig, File installLocation) {
    List<String> options = new ArrayList<>();
    // start command
    options.add(getStartCmd(installLocation));

    // add -n if applicable
    if (!(serverSymbolicName.getSymbolicName().contains(":") || (serverSymbolicName.getSymbolicName().isEmpty()))) {
      options.add("-n");
      options.add(serverSymbolicName.getSymbolicName());
    }

    // add -f if applicable
    if (tcConfig.getPath() != null) {
      //workaround to have unique platform restart directory for active & passives
      //TODO this can  be removed when platform persistent has server name in the path
      try {
        String modifiedTcConfigPath = tcConfig.getPath()
            .substring(0, tcConfig.getPath()
                .length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
        String modifiedConfig = FileUtils.readFileToString(new File(tcConfig.getPath())).
            replaceAll(Pattern.quote("${restart-data}"), String.valueOf("restart-data/" + serverSymbolicName)).
            replaceAll(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
        FileUtils.write(new File(modifiedTcConfigPath), modifiedConfig);
        options.add("-f");
        options.add(modifiedTcConfigPath);
      } catch (IOException ioe) {
        throw new RuntimeException("Error when modifying tc config", ioe);
      }
    }

    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.debug("TSA create command = {}", sb.toString());

    return options;
  }

  private String getStartCmd(File installLocation) {
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
        .map(s -> s.getHostname() + ":" + proxyTsaPorts.getOrDefault(s.getServerSymbolicName(), s.getPorts().getTsaPort()))
        .collect(Collectors.joining(",", "", "")));
  }

  @Override
  public String clientJarsRootFolderName() {
    return "apis";
  }

}
