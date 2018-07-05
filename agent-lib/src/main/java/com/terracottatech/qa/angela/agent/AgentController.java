package com.terracottatech.qa.angela.agent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.agent.kit.RemoteKitManager;
import com.terracottatech.qa.angela.agent.kit.TerracottaInstall;
import com.terracottatech.qa.angela.agent.kit.TmsInstall;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.FileMetadata;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import com.terracottatech.qa.angela.common.util.LogOutputStream;
import com.terracottatech.qa.angela.common.util.OS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */

public class AgentController {

  private final static Logger logger = LoggerFactory.getLogger(AgentController.class);

  private final JavaLocationResolver javaLocationResolver = new JavaLocationResolver();
  private final Map<InstanceId, TerracottaInstall> kitsInstalls = new HashMap<>();
  private final Map<InstanceId, TmsInstall> tmsInstalls = new HashMap<>();
  private final Ignite ignite;

  AgentController(Ignite ignite) {
    this.ignite = ignite;
  }

  public boolean attemptRemoteInstallation(InstanceId instanceId, Topology topology, TerracottaServer terracottaServer, boolean offline, License license, int tcConfigIndex, SecurityRootDirectory securityRootDirectory, final String kitInstallationName) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);

    if (terracottaInstall == null) {
      logger.info("Installing kit for " + terracottaServer);
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, topology.getDistribution(), kitInstallationName);

      boolean isKitAvailable = kitManager.verifyKitAvailability(offline);
      if (isKitAvailable) {
        File kitDir = kitManager.installKit(license);

        installSecurityRootDirectory(securityRootDirectory, kitDir, terracottaServer, topology, tcConfigIndex);

        logger.info("Installing the tc-configs");
        for (TcConfig tcConfig : topology.getTcConfigs()) {
          tcConfig.updateLogsLocation(kitDir, tcConfigIndex);
          tcConfig.writeTcConfigFile(kitDir);
          logger.info("Tc Config installed config path : {}", tcConfig.getPath());
        }
        terracottaInstall = new TerracottaInstall(topology, kitDir, license.getFilename());
        kitsInstalls.put(instanceId, terracottaInstall);
      } else {
        return false;
      }
    } else {
      logger.info("Kit for " + terracottaServer + " already installed");
      installSecurityRootDirectory(securityRootDirectory, terracottaInstall.getInstallLocation(), terracottaServer, topology, tcConfigIndex);
    }

    TcConfig tcConfig = topology.get(tcConfigIndex);
    tcConfig.updateLogsLocation(terracottaInstall.getInstallLocation(), tcConfigIndex);
    terracottaInstall.addServer(terracottaServer, tcConfig);

    return true;
  }

  public void downloadKit(final InstanceId instanceId, Distribution distribution, final String kitInstallationName) {
    final BlockingQueue<Object> queue = ignite.queue(instanceId + "@file-transfer-queue@tsa", 100, new CollectionConfiguration());
    try {
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);

      File kitDestDir = kitManager.getKitInstallationPath().getParentFile();
      logger.info("Downloading tsa jars into {}", kitDestDir);
      if (!kitDestDir.mkdirs()) {
        throw new RuntimeException("Cannot create client directory '" + kitDestDir);
      }

      while (true) {
        Object read = queue.take();
        if (read.equals(Boolean.TRUE)) {
          logger.info("Downloaded jar into {}", kitDestDir);
          break;
        }

        FileMetadata fileMetadata = (FileMetadata)read;
        logger.debug("downloading " + fileMetadata);
        if (!fileMetadata.isDirectory()) {
          long readFileLength = 0L;
          File file = new File(kitDestDir + File.separator + fileMetadata.getPathName());
          file.getParentFile().mkdirs();
          try (FileOutputStream fos = new FileOutputStream(file)) {
            while (true) {
              if (readFileLength == fileMetadata.getLength()) {
                break;
              }
              if (readFileLength > fileMetadata.getLength()) {
                throw new RuntimeException("Error creating client classpath ");
              }

              byte[] buffer = (byte[])queue.take();
              fos.write(buffer);
              readFileLength += buffer.length;
            }
          }
          logger.debug("downloaded " + fileMetadata);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot upload jar", e);
    }
  }

  public void install(InstanceId instanceId, Topology topology, TerracottaServer terracottaServer, boolean offline, License license, int tcConfigIndex, SecurityRootDirectory securityRootDirectory, final String kitInstallationName) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      logger.info("Installing kit for " + terracottaServer);
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, topology.getDistribution(), kitInstallationName);

      File kitDir = kitManager.installKit(license);

      installSecurityRootDirectory(securityRootDirectory, kitDir, terracottaServer, topology, tcConfigIndex);
      logger.info("Installing the tc-configs");
      for (TcConfig tcConfig : topology.getTcConfigs()) {
        tcConfig.updateLogsLocation(kitDir, tcConfigIndex);
        tcConfig.writeTcConfigFile(kitDir);
        logger.info("Tc Config installed config path : {}", tcConfig.getPath());
      }
      terracottaInstall = new TerracottaInstall(topology, kitDir, license.getFilename());
      kitsInstalls.put(instanceId, terracottaInstall);
    } else {
      logger.info("Kit for " + terracottaServer + " already installed");
      installSecurityRootDirectory(securityRootDirectory, terracottaInstall.getInstallLocation(), terracottaServer, topology, tcConfigIndex);
    }

    TcConfig tcConfig = topology.get(tcConfigIndex);
    tcConfig.updateLogsLocation(terracottaInstall.getInstallLocation(), tcConfigIndex);
    terracottaInstall.addServer(terracottaServer, tcConfig);
  }

  public String getInstallPath(InstanceId instanceId, TerracottaServer terracottaServer) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    TerracottaServerInstance terracottaServerInstance = terracottaInstall.getTerracottaServerInstance(terracottaServer);
    if (terracottaServerInstance == null) {
      throw new IllegalStateException("Server " + terracottaServer + " has not been installed");
    }
    return terracottaInstall.getInstallLocation().getPath();
  }

  public String getLicensePath(InstanceId instanceId) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      throw new IllegalStateException("Server has not been installed");
    }
    return terracottaInstall.getLicenseFileLocation().getPath();
  }

  private void installSecurityRootDirectory(SecurityRootDirectory securityRootDirectory, File installLocation,
                                            TerracottaServer terracottaServer,
                                            Topology topology, int tcConfigIndex) {
    if (securityRootDirectory != null) {
      final String serverName = terracottaServer.getServerSymbolicName().getSymbolicName();
      Path securityRootDirectoryPath = installLocation.toPath().resolve("security-root-directory-" + serverName);
      logger.info("Installing SecurityRootDirectory in {} for server {}", securityRootDirectoryPath, serverName);
      securityRootDirectory.createSecurityRootDirectory(securityRootDirectoryPath);
      topology.get(tcConfigIndex).updateSecurityRootDirectoryLocation(securityRootDirectoryPath.toString());
    }
  }

  public boolean attemptRemoteTmsInstallation(final InstanceId instanceId, final String tmsHostname,
                                              final Distribution distribution, final boolean offline, final License license,
                                              final TmsServerSecurityConfig tmsServerSecurityConfig, final String kitInstallationName,
                                              TerracottaCommandLineEnvironment tcEnv) {
    TmsInstall tmsInstall = tmsInstalls.get(instanceId);
    if (tmsInstall != null) {
      logger.info("Kit for " + tmsHostname + " already installed");
      tmsInstall.addTerracottaManagementServer();
      return true;
    } else {
      logger.info("Attempting to install kit from cached install for " + tmsHostname);
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);

      boolean isKitAvailable = kitManager.verifyKitAvailability(offline);
      if (isKitAvailable) {
        File kitDir = kitManager.installKit(license);
        File tmcProperties = new File(kitDir, "/tools/management/conf/tmc.properties");
        if (tmsServerSecurityConfig != null) {
          enableSecurity(tmcProperties, tmsServerSecurityConfig);
        }
        tmsInstalls.put(instanceId, new TmsInstall(distribution, kitDir, tcEnv));
        return true;
      } else {
        return false;
      }
    }
  }

  public void installTms(InstanceId instanceId, String tmsHostname, Distribution distribution, License license, TmsServerSecurityConfig tmsServerSecurityConfig, String kitInstallationName, TerracottaCommandLineEnvironment tcEnv) {
    TmsInstall tmsInstall = tmsInstalls.get(instanceId);
    if (tmsInstall != null) {
      logger.info("Kit for " + tmsHostname + " already installed");
      tmsInstall.addTerracottaManagementServer();
    } else {
      logger.info("Installing kit for " + tmsHostname);
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);
      File kitDir = kitManager.installKit(license);
      File tmcProperties = new File(kitDir, "/tools/management/conf/tmc.properties");
      if (tmsServerSecurityConfig != null) {
        enableSecurity(tmcProperties, tmsServerSecurityConfig);
      }

      tmsInstalls.put(instanceId, new TmsInstall(distribution, kitDir, tcEnv));
    }
  }

  private void enableSecurity(File tmcProperties, TmsServerSecurityConfig tmsServerSecurityConfig) {
    String tmsSecurityRootDirectory = adaptToWindowsPaths(tmsServerSecurityConfig.getTmsSecurityRootDirectory());
    String clusterSecurityRootDirectory = adaptToWindowsPaths(tmsServerSecurityConfig.getClusterSecurityRootDirectory());

    List<String> lines = new ArrayList<>();
    String line;
    try (BufferedReader br = new BufferedReader(new FileReader(tmcProperties))) {

      while ((line = br.readLine()) != null) {
        if (line.startsWith("tms.security.root.directory=")) {
          line = line.replace("tms.security.root.directory=", "tms.security.root.directory=" + tmsSecurityRootDirectory);
        } else if (line.startsWith("tms.security.root.directory.connection.default=")) {
          line = line.replace("tms.security.root.directory.connection.default=", "tms.security.root.directory.connection.default=" + clusterSecurityRootDirectory);
        } else if (line.equals("tms.security.https.enabled=false")) {
          line = line.replace("tms.security.https.enabled=false", "tms.security.https.enabled=true");
        }
        lines.add(line);
      }

      try (BufferedWriter out = new BufferedWriter(new FileWriter(tmcProperties))) {
        for (String s : lines) {
          out.write(s);
          out.newLine();
        }
        out.flush();
      }
    } catch (Exception ex) {
      throw new RuntimeException("Unable to enable security in TMS tmc.properties file", ex);
    }
  }

  private String adaptToWindowsPaths(Path path) {
    String pathAsString = path.toString();
    if (OS.INSTANCE.isWindows()) {
      //Replace "\" with "\\"
      pathAsString = pathAsString.replace("\\", "\\\\");
      return pathAsString;
    }
    return pathAsString;
  }

  public void startTms(final InstanceId instanceId) {
    TerracottaManagementServerInstance serverInstance = tmsInstalls.get(instanceId)
        .getTerracottaManagementServerInstance();
    serverInstance.start();
  }

  public void stopTms(final InstanceId instanceId) {
    TerracottaManagementServerInstance serverInstance = tmsInstalls.get(instanceId)
        .getTerracottaManagementServerInstance();
    serverInstance.stop();
  }

  public String getTmsInstallationPath(final InstanceId instanceId) {
    TmsInstall serverInstance = tmsInstalls.get(instanceId);
    return serverInstance.getInstallLocation().getPath();
  }

  public TerracottaManagementServerState getTerracottaManagementServerState(final InstanceId instanceId) {
    TmsInstall terracottaInstall = tmsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      return TerracottaManagementServerState.NOT_INSTALLED;
    }
    TerracottaManagementServerInstance serverInstance = terracottaInstall.getTerracottaManagementServerInstance();
    if (serverInstance == null) {
      return TerracottaManagementServerState.NOT_INSTALLED;
    }
    return serverInstance.getTerracottaManagementServerState();
  }

  public void uninstall(InstanceId instanceId, Topology topology, TerracottaServer terracottaServer, final String kitInstallationName) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall != null) {
      int installationsCount = terracottaInstall.removeServer(terracottaServer);
      TmsInstall tmsInstall = tmsInstalls.get(instanceId);
      if (installationsCount == 0 && (tmsInstall == null || tmsInstall.getTerracottaManagementServerInstance() == null)) {
        try {
          logger.info("Uninstalling kit for {}", terracottaServer);
          RemoteKitManager kitManager = new RemoteKitManager(instanceId, topology.getDistribution(), kitInstallationName);
          // TODO : get log files

          kitManager.deleteInstall(terracottaInstall.getInstallLocation());
          kitsInstalls.remove(instanceId);
        } catch (IOException ioe) {
          throw new RuntimeException("Unable to uninstall kit at " + terracottaInstall.getInstallLocation()
              .getAbsolutePath(), ioe);
        }
      } else {
        logger.info("Kit install still in use by {} Terracotta servers",
            installationsCount + (tmsInstall == null ? 0 : tmsInstall.getTerracottaManagementServerInstance() == null ? 0 : 1));
      }
    } else {
      logger.info("No installed kit for " + topology);
    }
  }


  public void uninstallTms(InstanceId instanceId, Distribution distribution, String kitInstallationName, String tmsHostname) {
    TmsInstall tmsInstall = tmsInstalls.get(instanceId);
    if (tmsInstall != null) {
      tmsInstall.removeServer();
      TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
      int numberOfTerracottaInstances = (terracottaInstall != null ? terracottaInstall.numberOfTerracottaInstances() : 0);
      if (numberOfTerracottaInstances == 0) {
        try {
          logger.info("Uninstalling kit for " + tmsHostname);
          RemoteKitManager kitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);
          // TODO : get log files

          kitManager.deleteInstall(tmsInstall.getInstallLocation());
          kitsInstalls.remove(instanceId);
        } catch (IOException ioe) {
          throw new RuntimeException("Unable to uninstall kit at " + tmsInstall.getInstallLocation()
              .getAbsolutePath(), ioe);
        }
      } else {
        logger.info("Kit install still in use by {} Terracotta servers", numberOfTerracottaInstances);
      }
    } else {
      logger.info("No installed kit for " + tmsHostname);
    }
  }

  public void create(final InstanceId instanceId, final TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(terracottaServer);
    serverInstance.create(tcEnv);

  }

  public void stop(final InstanceId instanceId, final TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      return;
    }
    TerracottaServerInstance serverInstance = terracottaInstall.getTerracottaServerInstance(terracottaServer);
    serverInstance.stop(tcEnv);
  }

  public void waitForState(final InstanceId instanceId, final TerracottaServer terracottaServer, Set<TerracottaServerState> wanted) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId)
        .getTerracottaServerInstance(terracottaServer);
    serverInstance.waitForState(wanted::contains);
  }

  public TerracottaServerState getTerracottaServerState(final InstanceId instanceId, final TerracottaServer terracottaServer) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      return TerracottaServerState.NOT_INSTALLED;
    }
    TerracottaServerInstance serverInstance = terracottaInstall.getTerracottaServerInstance(terracottaServer);
    if (serverInstance == null) {
      return TerracottaServerState.NOT_INSTALLED;
    }
    return serverInstance.getTerracottaServerState();
  }


  public void disrupt(final InstanceId instanceId, final TerracottaServer src, final TerracottaServer target) {
    disrupt(instanceId, src, Collections.singleton(target));
  }

  public void disrupt(final InstanceId instanceId, final TerracottaServer src, final Collection<TerracottaServer> targets) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(src);
    serverInstance.disrupt(targets);
  }

  public void undisrupt(final InstanceId instanceId, final TerracottaServer src, final TerracottaServer target) {
    undisrupt(instanceId, src, Collections.singleton(target));
  }

  public void undisrupt(final InstanceId instanceId, final TerracottaServer src, final Collection<TerracottaServer> targets) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(src);
    serverInstance.undisrupt(targets);
  }

  public void configureLicense(final InstanceId instanceId, final TerracottaServer terracottaServer, final TcConfig[] tcConfigs,
                               String clusterName, final SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv,
                               boolean verbose) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(terracottaServer);
    String licensePath = getLicensePath(instanceId);
    if (clusterName == null) {
      clusterName = instanceId.toString();
    }
    serverInstance.configureLicense(clusterName, licensePath, tcConfigs, securityRootDirectory, tcEnv, verbose);
  }

  public ClusterToolExecutionResult clusterTool(InstanceId instanceId, TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      throw new IllegalStateException("Cannot control cluster tool: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return terracottaInstall.getTerracottaServerInstance(terracottaServer).clusterTool(tcEnv, arguments);
  }

  public void destroyClient(InstanceId instanceId, int pid) {
    try {
      logger.info("killing client '{}' with PID {}", instanceId, pid);
      PidProcess pidProcess = Processes.newPidProcess(pid);
      ProcessUtil.destroyGracefullyOrForcefullyAndWait(pidProcess, 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);

      File subAgentRoot = clientRootDir(instanceId);
      logger.info("cleaning up directory structure '{}' of client {}", subAgentRoot, instanceId);
      FileUtils.deleteDirectory(subAgentRoot);
    } catch (Exception e) {
      throw new RuntimeException("Error cleaning up client " + instanceId, e);
    }
  }

  public int spawnClient(InstanceId instanceId, boolean localhostOnly, TerracottaCommandLineEnvironment tcEnv) {
    try {
      String javaHome = javaLocationResolver.resolveJavaLocation(tcEnv).getHome();

      final AtomicBoolean started = new AtomicBoolean(false);
      List<String> cmdLine = new ArrayList<>();
      if (OS.INSTANCE.isWindows()) {
        cmdLine.add(javaHome + "\\bin\\java.exe");
      } else {
        cmdLine.add(javaHome + "/bin/java");
      }
      if (tcEnv.getJavaOpts() != null) {
        cmdLine.addAll(tcEnv.getJavaOpts());
      }
      cmdLine.add("-classpath");
      cmdLine.add(buildClasspath(instanceId));
      if (localhostOnly) {
        cmdLine.add("-Dtc.qa.directjoin=localhost:40000");
      }
      cmdLine.add("-Dtc.qa.nodeName=" + instanceId);
      cmdLine.add("-D" + Agent.ROOT_DIR_SYSPROP_NAME + "=" + Agent.ROOT_DIR);
      cmdLine.add(Agent.class.getName());

      logger.info("Spawning client {}", cmdLine);
      ProcessExecutor processExecutor = new ProcessExecutor().command(cmdLine)
          .redirectOutput(new LogOutputStream() {
            @Override
            protected void processLine(String line) {
              System.out.println(" |" + instanceId + "| " + line);
              if (line.equals(Agent.AGENT_IS_READY_MARKER_LOG)) {
                started.set(true);
              }
            }
          }).directory(clientRootDir(instanceId));
      StartedProcess startedProcess = processExecutor.start();

      while (startedProcess.getProcess().isAlive() && !started.get()) {
        Thread.sleep(100);
      }
      if (!startedProcess.getProcess().isAlive()) {
        throw new RuntimeException("Client process died in infancy");
      }

      int pid = PidUtil.getPid(startedProcess.getProcess());
      logger.info("Spawned client with PID {}", pid);
      return pid;
    } catch (Exception e) {
      throw new RuntimeException("Error spawning client " + instanceId, e);
    }
  }

  private static String buildClasspath(InstanceId instanceId) {
    File subClientDir = new File(clientRootDir(instanceId), "lib");
    String[] cpEntries = subClientDir.list();
    if (cpEntries == null) {
      throw new IllegalStateException("No client to spawn from " + instanceId);
    }

    StringBuilder sb = new StringBuilder();
    for (String cpentry : cpEntries) {
      sb.append("lib").append(File.separator).append(cpentry).append(File.pathSeparator);
    }

    // if
    //   file:/Users/lorban/.m2/repository/org/slf4j/slf4j-api/1.7.22/slf4j-api-1.7.22.jar!/org/slf4j/Logger.class
    // else
    //   /work/terracotta/irepo/lorban/angela/agent/target/classes/com/terracottatech/qa/angela/agent/Agent.class

    String agentClassName = Agent.class.getName().replace('.', '/');
    String agentClassPath = Agent.class.getResource("/" + agentClassName + ".class").getPath();

    if (agentClassPath.startsWith("file:")) {
      sb.append(agentClassPath.substring("file:".length(), agentClassPath.lastIndexOf('!')));
    } else {
      sb.append(agentClassPath.substring(0, agentClassPath.lastIndexOf(agentClassName)));
    }

    return sb.toString();
  }

  public void downloadClient(InstanceId instanceId) {
    final BlockingQueue<Object> queue = ignite.queue("file-transfer-queue@" + instanceId, 100, new CollectionConfiguration());
    try {
      File subClientDir = new File(clientRootDir(instanceId), "lib");
      logger.info("Downloading client '{}' into {}", instanceId, subClientDir);
      if (!subClientDir.mkdirs()) {
        throw new RuntimeException("Cannot create client directory '" + subClientDir + "' on " + instanceId);
      }

      while (true) {
        Object read = queue.take();
        if (read.equals(Boolean.TRUE)) {
          logger.info("Downloaded client '{}' into {}", instanceId, subClientDir);
          break;
        }

        FileMetadata fileMetadata = (FileMetadata)read;
        logger.debug("downloading " + fileMetadata);
        if (!fileMetadata.isDirectory()) {
          long readFileLength = 0L;
          File file = new File(subClientDir + File.separator + fileMetadata.getPathName());
          file.getParentFile().mkdirs();
          try (FileOutputStream fos = new FileOutputStream(file)) {
            while (true) {
              if (readFileLength == fileMetadata.getLength()) {
                break;
              }
              if (readFileLength > fileMetadata.getLength()) {
                throw new RuntimeException("Error creating client classpath on " + instanceId);
              }

              byte[] buffer = (byte[])queue.take();
              fos.write(buffer);
              readFileLength += buffer.length;
            }
          }
          logger.debug("downloaded " + fileMetadata);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot upload client on " + instanceId, e);
    }
  }


  private static File clientRootDir(InstanceId instanceId) {
    return new File(Agent.ROOT_DIR + File.separator + "work" + File.separator + instanceId);
  }

  private static File instanceRootDir(InstanceId instanceId) {
    return new File(Agent.ROOT_DIR, instanceId.toString());
  }

  public void cleanup(InstanceId instanceId) {
    logger.info("Cleaning up instance {}", instanceId);
    try {
      FileUtils.deleteDirectory(instanceRootDir(instanceId));
    } catch (IOException ioe) {
      throw new RuntimeException("Error cleaning up instance root directory : " + instanceRootDir(instanceId), ioe);
    }
  }

  public List<String> listFiles(String folder) {
    File[] files = new File(folder).listFiles(pathname -> !pathname.isDirectory());
    if (files == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(files).map(File::getName).collect(toList());
  }

  public List<String> listFolders(String folder) {
    File[] files = new File(folder).listFiles(pathname -> pathname.isDirectory());
    if (files == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(files).map(File::getName).collect(toList());
  }

  public byte[] downloadFile(String file) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (FileInputStream fis = new FileInputStream(file)) {
      IOUtils.copy(fis, baos);
    } catch (IOException ioe) {
      throw new RuntimeException("Error downloading file " + file, ioe);
    }
    return baos.toByteArray();
  }

  public void uploadFile(String filename, byte[] data) {
    File file = new File(filename);
    file.getParentFile().mkdirs();
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(data);
    } catch (IOException ioe) {
      throw new RuntimeException("Error uploading file " + filename, ioe);
    }
  }

  public byte[] downloadFolder(String file) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      File root = new File(file);
      zipFolder(zos, "", root);
    } catch (IOException ioe) {
      throw new RuntimeException("Error downloading folder " + file, ioe);
    }
    return baos.toByteArray();
  }

  private void zipFolder(ZipOutputStream zos, String parent, File folder) throws IOException {
    if (!folder.canRead()) {
      throw new RuntimeException("Folder does not exist or is not readable : " + folder);
    }
    File[] files = folder.listFiles();
    if (files == null) {
      throw new RuntimeException("Error listing folder " + folder);
    }
    for (File file : files) {
      if (file.isDirectory()) {
        zipFolder(zos, parent + file.getName() + "/", file);
      } else {
        ZipEntry zipEntry = new ZipEntry(parent + file.getName());
        zipEntry.setTime(file.lastModified());
        zos.putNextEntry(zipEntry);
        try (FileInputStream fis = new FileInputStream(file)) {
          IOUtils.copy(fis, zos);
        }
        zos.closeEntry();
      }
    }
  }

  public Map<String, ?> getNodeAttributes() {
    return ignite.configuration().getUserAttributes();
  }

}
