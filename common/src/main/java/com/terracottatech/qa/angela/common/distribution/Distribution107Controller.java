package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.ProcessUtil;
import com.terracottatech.qa.angela.common.util.TriggeringOutputStream;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
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

import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_IN_DIAGNOSTIC_MODE;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static java.lang.Integer.parseInt;
import static java.util.regex.Pattern.compile;

public class Distribution107Controller extends DistributionController {
  private final static Logger logger = LoggerFactory.getLogger(Distribution102Controller.class);

  public Distribution107Controller(Distribution distribution) {
    super(distribution);
  }

  @Override
  public void disrupt(ServerSymbolicName serverSymbolicName, Collection<TerracottaServer> targets, boolean netDisruptionEnabled) {
    //TODO: TDB-4770
  }

  @Override
  public void undisrupt(ServerSymbolicName serverSymbolicName, Collection<TerracottaServer> targets, boolean netDisruptionEnabled) {
    //TODO: TDB-4770
  }

  @Override
  public void removeDisruptionLinks(ServerSymbolicName serverSymbolicName, boolean netDisruptionEnabled) {
    //TODO: TDB-4770
  }

  @Override
  public TerracottaServerInstance.TerracottaServerInstanceProcess createTsa(TerracottaServer terracottaServer, File installLocation, Topology topology, TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs) {
    Map<String, String> env = buildEnv(tcEnv);
    AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>(STOPPED);
    AtomicInteger javaPid = new AtomicInteger(-1);

    TriggeringOutputStream serverLogOutputStream = TriggeringOutputStream.triggerOn(
        compile("^.*\\QStarted the server in diagnostic mode\\E.*$"), mr -> stateRef.set(STARTED_IN_DIAGNOSTIC_MODE)
    ).andTriggerOn(
        compile("^.*PID is (\\d+).*$"), mr -> {
          javaPid.set(parseInt(mr.group(1)));
        }
    );
    WatchedProcess watchedProcess = new WatchedProcess<>(new ProcessExecutor()
        .command(createTsaCommand(terracottaServer, installLocation, startUpArgs))
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

  private List<String> createTsaCommand(TerracottaServer terracottaServer, File installLocation, List<String> startUpArgs) {
    List<String> options = new ArrayList<>();
    options.add(getTsaCreateExecutable(installLocation));
    options.addAll(startUpArgs);
    List<String> dynamicArguments = addDynamicServerArguments(terracottaServer);
    options.addAll(dynamicArguments);
    StringBuilder sb = new StringBuilder();
    for (String option : options) {
      sb.append(option).append(" ");
    }
    logger.debug("Create TSA command = {}", sb.toString());

    return options;
  }

  private List<String> addDynamicServerArguments(TerracottaServer terracottaServer) {
    List<String> res = new ArrayList<>();
    res.add("-n");
    res.add(terracottaServer.getServerSymbolicName().getSymbolicName());
    if (terracottaServer.getTsaPort() != 0) {
      res.add("-p");
      res.add(String.valueOf(terracottaServer.getTsaPort()));
    }
    if (terracottaServer.getTsaGroupPort() != 0) {
      res.add("-g");
      res.add(String.valueOf(terracottaServer.getTsaGroupPort()));
    }
    if (terracottaServer.getRepository() != null) {
      res.add("-r");
      res.add(terracottaServer.getRepository());
    }
    if (terracottaServer.getMetaData() != null) {
      res.add("-m");
      res.add(terracottaServer.getMetaData());
    }
    if (terracottaServer.getLogs() != null) {
      res.add("-L");
      res.add(terracottaServer.getLogs());
    }
    return res;
  }

  private String getTsaCreateExecutable(File installLocation) {
    if (distribution.getPackageType() == KIT) {
      return installLocation.getAbsolutePath() + File.separator + "server" + File.separator + "bin" + File.separator + "start-node" +
          OS.INSTANCE.getShellExtension();
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      return installLocation.getAbsolutePath() + File.separator + "TerracottaDB"
          + File.separator + "server" + File.separator + "bin" + File.separator + "start-node" +
          OS.INSTANCE.getShellExtension();
    }
    throw new IllegalStateException("Can not define Terracotta server Start Command for distribution: " + distribution);
  }

  @Override
  public TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment env) {
    //TODO: TDB-4771
    return null;
  }

  @Override
  public void stopTms(File installLocation, TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
    //TODO: TDB-4771
  }

  @Override
  public void stopTsa(ServerSymbolicName serverSymbolicName, File location, TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv) {
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
  public void configureTsaLicense(String clusterName, File location, String licensePath, List<TcConfig> tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv, boolean verbose) {
    throw new UnsupportedOperationException("Dynamic config don't use this flow for licensing");
  }

  @Override
  public ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment env, String... arguments) {
    throw new UnsupportedOperationException("Dynamic config don't use cluster-tool");
  }

  @Override
  public ConfigToolExecutionResult invokeConfigTool(File installLocation, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    List<String> command = new ArrayList<>();
    command.add(installLocation
        + File.separator + "tools"
        + File.separator + "config-tool"
        + File.separator + "bin"
        + File.separator + "config-tool" + OS.INSTANCE.getShellExtension());
    command.addAll(Arrays.asList(arguments));

    try {
      ProcessResult processResult = new ProcessExecutor(command)
          .directory(installLocation)
          .environment(buildEnv(tcEnv))
          .redirectErrorStream(true)
          .readOutput(true)
          .execute();
      return new ConfigToolExecutionResult(processResult.getExitValue(), processResult.getOutput().getLines());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public URI tsaUri(Collection<TerracottaServer> servers, Map<ServerSymbolicName, Integer> proxyTsaPorts) {
    //TODO: TDB-4771
    return null;
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
    return null;
  }

}