package com.terracottatech.qa.angela.agent;

import com.terracottatech.qa.angela.agent.client.RemoteClientManager;
import com.terracottatech.qa.angela.agent.kit.MonitoringInstance;
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
import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.metrics.MonitoringCommand;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.FileMetadata;
import com.terracottatech.qa.angela.common.util.IgniteCommonHelper;
import com.terracottatech.qa.angela.common.util.ProcessUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */

public class AgentController {

  private final static Logger logger = LoggerFactory.getLogger(AgentController.class);

  private final Map<InstanceId, TerracottaInstall> kitsInstalls = new HashMap<>();
  private final Map<InstanceId, TmsInstall> tmsInstalls = new HashMap<>();
  private final Ignite ignite;
  private final Collection<String> joinedNodes;
  private volatile MonitoringInstance monitoringInstance;

  AgentController(Ignite ignite, Collection<String> joinedNodes) {
    this.ignite = ignite;
    this.joinedNodes = Collections.unmodifiableList(new ArrayList<>(joinedNodes));
  }

  public boolean installTsa(InstanceId instanceId, Topology topology, TerracottaServer terracottaServer, boolean offline, License license,
                            SecurityRootDirectory securityRootDirectory, String kitInstallationName, Distribution distribution) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);

    File installLocation;
    if (terracottaInstall == null || !terracottaInstall.installed(distribution)) {
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);
      boolean isKitAvailable = kitManager.verifyKitAvailability(offline);
      if (!isKitAvailable) {
        return false;
      }

      logger.info("Installing kit for {} from {}", terracottaServer, distribution);
      installLocation = kitManager.installKit(license);
      terracottaInstall = kitsInstalls.computeIfAbsent(instanceId, (iid) -> new TerracottaInstall(kitManager.getWorkingKitInstallationPath()));
    } else {
      installLocation = terracottaInstall.installLocation(distribution);
      logger.info("Kit for {} already installed", terracottaServer);
    }

    terracottaInstall.addServer(terracottaServer, securityRootDirectory, installLocation, license, distribution, topology);

    return true;
  }

  public String getTsaInstallPath(InstanceId instanceId, TerracottaServer terracottaServer) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    TerracottaServerInstance terracottaServerInstance = terracottaInstall.getTerracottaServerInstance(terracottaServer);
    if (terracottaServerInstance == null) {
      throw new IllegalStateException("Server " + terracottaServer + " has not been installed");
    }
    return terracottaInstall.getInstallLocation(terracottaServer).getPath();
  }

  public String getTsaLicensePath(InstanceId instanceId, TerracottaServer terracottaServer) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      throw new IllegalStateException("Server has not been installed");
    }
    File licenseFileLocation = terracottaInstall.getLicenseFileLocation(terracottaServer);
    return licenseFileLocation == null ? null : licenseFileLocation.getPath();
  }

  public boolean installTms(InstanceId instanceId, String tmsHostname,
                            Distribution distribution, boolean offline, License license,
                            TmsServerSecurityConfig tmsServerSecurityConfig, String kitInstallationName,
                            TerracottaCommandLineEnvironment tcEnv) {
    TmsInstall tmsInstall = tmsInstalls.get(instanceId);
    if (tmsInstall != null) {
      logger.debug("Kit for " + tmsHostname + " already installed");
      tmsInstall.addTerracottaManagementServer();
      return true;
    } else {
      logger.debug("Attempting to install kit from cached install for " + tmsHostname);
      RemoteKitManager kitManager = new RemoteKitManager(instanceId, distribution, kitInstallationName);

      boolean isKitAvailable = kitManager.verifyKitAvailability(offline);
      if (isKitAvailable) {
        File kitDir = kitManager.installKit(license);
        File tmcProperties = new File(kitDir, "/tools/management/conf/tmc.properties");
        if (tmsServerSecurityConfig != null) {
          enableTmsSecurity(tmcProperties, tmsServerSecurityConfig);
        }
        tmsInstalls.put(instanceId, new TmsInstall(distribution, kitDir, tcEnv));
        return true;
      } else {
        return false;
      }
    }
  }

  private void enableTmsSecurity(File tmcProperties, TmsServerSecurityConfig tmsServerSecurityConfig) {
    Properties properties = new Properties();

    try (InputStream inputStream = new FileInputStream(tmcProperties)) {
      properties.load(inputStream);
    } catch (Exception ex) {
      throw new RuntimeException("Unable to enable security in TMS tmc.properties file", ex);
    }

    tmsServerSecurityConfig.toMap().forEach((key, value) -> {
      if (value == null) {
        properties.remove(key);
      } else {
        properties.put(key, value);
      }
    });

    try (OutputStream outputStream = new FileOutputStream(tmcProperties)) {
      properties.store(outputStream, null);
    } catch (Exception ex) {
      throw new RuntimeException("Unable to enable security in TMS tmc.properties file", ex);
    }
  }

  public void startTms(InstanceId instanceId) {
    TerracottaManagementServerInstance serverInstance = tmsInstalls.get(instanceId)
        .getTerracottaManagementServerInstance();
    serverInstance.start();
  }

  public void stopTms(InstanceId instanceId) {
    TerracottaManagementServerInstance serverInstance = tmsInstalls.get(instanceId)
        .getTerracottaManagementServerInstance();
    serverInstance.stop();
  }

  public String getTmsInstallationPath(InstanceId instanceId) {
    TmsInstall serverInstance = tmsInstalls.get(instanceId);
    return serverInstance.getInstallLocation().getPath();
  }

  public TerracottaManagementServerState getTmsState(InstanceId instanceId) {
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

  public void uninstallTsa(InstanceId instanceId, Topology topology, TerracottaServer terracottaServer, String kitInstallationName) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall != null) {
      int installationsCount = terracottaInstall.removeServer(terracottaServer);
      TmsInstall tmsInstall = tmsInstalls.get(instanceId);
      if (installationsCount == 0 && (tmsInstall == null || tmsInstall.getTerracottaManagementServerInstance() == null)) {
        File installLocation = terracottaInstall.getRootInstallLocation();
        try {
          logger.info("Uninstalling kit(s) from {}", installLocation);
          RemoteKitManager kitManager = new RemoteKitManager(instanceId, topology.getDistribution(), kitInstallationName);
          // TODO : get log files

          kitManager.deleteInstall(installLocation);
          kitsInstalls.remove(instanceId);
        } catch (IOException ioe) {
          throw new RuntimeException("Unable to uninstall kit at " + installLocation.getAbsolutePath() + " on " + terracottaServer, ioe);
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
      int numberOfTerracottaInstances = (terracottaInstall != null ? terracottaInstall.terracottaServerInstanceCount() : 0);
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

  public void createTsa(InstanceId instanceId, TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(terracottaServer);
    serverInstance.create(tcEnv, startUpArgs);
  }

  public void stopTsa(InstanceId instanceId, TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      return;
    }
    TerracottaServerInstance serverInstance = terracottaInstall.getTerracottaServerInstance(terracottaServer);
    serverInstance.stop(tcEnv);
  }

  public void waitForTsaInState(InstanceId instanceId, TerracottaServer terracottaServer, Set<TerracottaServerState> wanted) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId)
        .getTerracottaServerInstance(terracottaServer);
    serverInstance.waitForState(wanted::contains);
  }

  public TerracottaServerState getTsaState(InstanceId instanceId, TerracottaServer terracottaServer) {
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


  public void disrupt(InstanceId instanceId, TerracottaServer src, TerracottaServer target) {
    disrupt(instanceId, src, Collections.singleton(target));
  }

  public void disrupt(InstanceId instanceId, TerracottaServer src, Collection<TerracottaServer> targets) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(src);
    serverInstance.disrupt(targets);
  }

  public void undisrupt(InstanceId instanceId, TerracottaServer src, TerracottaServer target) {
    undisrupt(instanceId, src, Collections.singleton(target));
  }

  public void undisrupt(InstanceId instanceId, TerracottaServer src, Collection<TerracottaServer> targets) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(src);
    serverInstance.undisrupt(targets);
  }

  public void configureTsaLicense(InstanceId instanceId, TerracottaServer terracottaServer, List<TcConfig> tcConfigs,
                                  String clusterName, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment tcEnv,
                                  boolean verbose) {
    TerracottaServerInstance serverInstance = kitsInstalls.get(instanceId).getTerracottaServerInstance(terracottaServer);
    String licensePath = getTsaLicensePath(instanceId, terracottaServer);
    if (clusterName == null) {
      clusterName = instanceId.toString();
    }
    serverInstance.configureTsaLicense(clusterName, licensePath, tcConfigs, securityRootDirectory, tcEnv, verbose);
  }

  public ClusterToolExecutionResult clusterTool(InstanceId instanceId, TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    TerracottaInstall terracottaInstall = kitsInstalls.get(instanceId);
    if (terracottaInstall == null) {
      throw new IllegalStateException("Cannot control cluster tool: server " + terracottaServer.getServerSymbolicName() + " has not been installed");
    }
    return terracottaInstall.getTerracottaServerInstance(terracottaServer).clusterTool(tcEnv, arguments);
  }

  public void startHardwareMonitoring(String workingPath, Map<HardwareMetric, MonitoringCommand> commands) {
    if (monitoringInstance == null) {
      monitoringInstance = new MonitoringInstance(new File(workingPath));
      monitoringInstance.startHardwareMonitoring(commands);
    }
  }

  public boolean isMonitoringRunning(HardwareMetric hardwareMetric) {
    return monitoringInstance.isMonitoringRunning(hardwareMetric);
  }

  public void stopHardwareMonitoring() {
    if (monitoringInstance != null) {
      monitoringInstance.stopHardwareMonitoring();
      monitoringInstance = null;
    }
  }

  public void stopClient(InstanceId instanceId, int pid) {
    try {
      logger.info("killing client '{}' with PID {}", instanceId, pid);
      ProcessUtil.destroyGracefullyOrForcefullyAndWait(pid);
    } catch (Exception e) {
      throw new RuntimeException("Error stopping client " + instanceId, e);
    }
  }

  public void deleteClient(InstanceId instanceId) {
    try {
      File subAgentRoot = new RemoteClientManager(instanceId).getClientInstallationPath();
      logger.info("cleaning up directory structure '{}' of client {}", subAgentRoot, instanceId);
      FileUtils.deleteDirectory(subAgentRoot);
    } catch (Exception e) {
      throw new RuntimeException("Error deleting client " + instanceId, e);
    }
  }

  public String instanceWorkDir(InstanceId instanceId) {
    File path = new File(Agent.WORK_DIR, instanceId.toString());
    return path.getAbsolutePath();
  }

  public int spawnClient(InstanceId instanceId, TerracottaCommandLineEnvironment tcEnv) {
    RemoteClientManager remoteClientManager = new RemoteClientManager(instanceId);
    return remoteClientManager.spawnClient(instanceId, tcEnv, joinedNodes);
  }

  public void downloadFiles(InstanceId instanceId, File installDir) {
    final BlockingQueue<Object> queue = IgniteCommonHelper.fileTransferQueue(ignite, instanceId);
    try {
      logger.info("Downloading files into {}", installDir);
      if (!installDir.exists()) {
        if (!installDir.mkdirs()) {
          throw new RuntimeException("Cannot create directory '" + installDir + "'");
        }
      }

      while (true) {
        Object read = queue.take();
        if (read.equals(Boolean.TRUE)) {
          logger.info("Downloaded files into {}", installDir);
          break;
        }

        FileMetadata fileMetadata = (FileMetadata)read;
        logger.debug("downloading " + fileMetadata);
        if (!fileMetadata.isDirectory()) {
          long readFileLength = 0L;
          File file = new File(installDir + File.separator + fileMetadata.getPathName());
          file.getParentFile().mkdirs();
          try (FileOutputStream fos = new FileOutputStream(file)) {
            while (readFileLength != fileMetadata.getLength()) {
              if (readFileLength > fileMetadata.getLength()) {
                throw new RuntimeException("Error downloading file : " + fileMetadata);
              }

              byte[] buffer = (byte[]) queue.take();
              fos.write(buffer);
              readFileLength += buffer.length;
            }
          }
          logger.debug("downloaded " + fileMetadata);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot download files to " + installDir.getAbsolutePath(), e);
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
    File[] files = new File(folder).listFiles(File::isDirectory);
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
      throw new IOException("Folder does not exist or is not readable : " + folder);
    }
    File[] files = folder.listFiles();
    if (files == null) {
      throw new IOException("Error listing folder " + folder);
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
