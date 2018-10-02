package com.terracottatech.qa.angela.client;

import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterMonitor implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(ClusterMonitor.class);
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final String workingKitInstallationPath;
  private final Set<String> hostnames;
  private boolean closed = false;

  ClusterMonitor(final Ignite ignite, final InstanceId instanceId, final Set<String> hostnames) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.workingKitInstallationPath = Agent.ROOT_DIR + File.separator + "work" + File.separator + instanceId;
    this.hostnames = hostnames;
  }


  public void startOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.startHardwareMonitoring(instanceId));
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error starting cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public void stopOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.stopHardwareMonitoring(instanceId));
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error stoping cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  public void downloadTo(File location) {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        new RemoteFolder(ignite, hostname, null, workingKitInstallationPath).downloadTo(new File(location, hostname));
      } catch (IOException e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error downloading cluster monitor remote files");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    stopOnAll();
  }

}
