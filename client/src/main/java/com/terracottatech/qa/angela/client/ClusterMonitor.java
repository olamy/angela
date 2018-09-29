package com.terracottatech.qa.angela.client;

import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterMonitor implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(ClusterMonitor.class);
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final String workingKitInstallationPath;
  private Set<String> tsaHostnames = new HashSet<>();
  private String tmsHostname = null;
  private Set<String> clientHostnames = new HashSet<>();
  private Map<String, Boolean> started = new ConcurrentHashMap<>();
  private boolean closed = false;

  public ClusterMonitor(final Ignite ignite, final InstanceId instanceId, final ConfigurationContext configurationContext) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.workingKitInstallationPath = Agent.ROOT_DIR + File.separator + "work" + File.separator + instanceId;

    initHostnamesList(configurationContext);
  }

  private void initHostnamesList(final ConfigurationContext configurationContext) {
    if (configurationContext.tsa() != null) {
      tsaHostnames.addAll(configurationContext.tsa().getTopology().getServersHostnames());
    }

    if (configurationContext.tms() != null) {
      this.tmsHostname = configurationContext.tms().getHostname();
    }

    if (configurationContext.clientArray() != null) {
      clientHostnames.addAll(configurationContext.clientArray().getClientArrayTopology().getClientHostnames());
    }
  }

  public void startOnAll() {
    startOnAllTsa();
    startOnAllTms();
    StartOnAllClients();
  }

  private void startOnAllTsa() {
    for (String tsaHostname : tsaHostnames) {
      startMonitoringOn(tsaHostname);
    }
  }

  private void startOnAllTms() {
    if (tmsHostname != null) {
      startMonitoringOn(tmsHostname);
    }
  }

  private void StartOnAllClients() {
    for (String clientHostname : clientHostnames) {
      startMonitoringOn(clientHostname);
    }
  }

  private void startMonitoringOn(final String hostname) {
    if (started.containsKey(hostname)) {
      if (!started.get(hostname)) {
        IgniteHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.startHardwareMonitoring(instanceId));
        started.put(hostname, true);
      }
    } else {
      IgniteHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.startHardwareMonitoring(instanceId));
      started.put(hostname, true);
    }

  }

  public void stopOnAll() {
    stopOnAllTsa();
    stopOnAllTms();
    StopOnAllClients();
  }

  private void stopOnAllTsa() {
    for (String tsaHostname : tsaHostnames) {
      stopMonitoringOn(tsaHostname);
    }
  }

  private void stopOnAllTms() {
    if (tmsHostname != null) {
      stopMonitoringOn(tmsHostname);
    }
  }

  private void StopOnAllClients() {
    for (String clientHostname : clientHostnames) {
      stopMonitoringOn(clientHostname);
    }
  }

  private void stopMonitoringOn(String hostname) {
    if (started.containsKey(hostname)) {
      if (started.get(hostname)) {
        IgniteHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.stopHardwareMonitoring(instanceId));
        started.put(hostname, false);
      }
    }

  }

  public void downloadTo(File location) {
    downloadAllTsaTo(location);
    downloadAllTmsTo(location);
    downloadAllClientsTo(location);
  }

  private void downloadAllTsaTo(final File location) {
    for (String tsaHostname : tsaHostnames) {
      try {
        new RemoteFolder(ignite, tsaHostname, null, workingKitInstallationPath).downloadTo(new File(location, tsaHostname));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void downloadAllTmsTo(final File location) {
    try {
      if (tmsHostname != null) {
        new RemoteFolder(ignite, tmsHostname, null, workingKitInstallationPath).downloadTo(new File(location, tmsHostname));
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void downloadAllClientsTo(final File location) {
    for (String clientHostname : clientHostnames) {
      try {
        new RemoteFolder(ignite, clientHostname, null, workingKitInstallationPath).downloadTo(new File(location, clientHostname));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    try {
      stopOnAll();
    } catch (Exception e) {
      logger.error("Error when trying to stop the ClusterMonitor: {}", e.getMessage());
    }
  }

}
