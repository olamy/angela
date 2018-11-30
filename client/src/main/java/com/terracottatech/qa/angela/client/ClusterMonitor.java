package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.filesystem.TransportableFile;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterMonitor implements AutoCloseable {

  private final Ignite ignite;
  private final File workingPath;
  private final Set<String> hostnames;
  private boolean closed = false;

  ClusterMonitor(Ignite ignite, InstanceId instanceId, Set<String> hostnames) {
    this.ignite = ignite;
    this.workingPath = new File(Agent.WORK_DIR, instanceId.toString());
    this.hostnames = hostnames;
  }

  public ClusterMonitor startOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteClientHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.startHardwareMonitoring(workingPath.getPath()));
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error starting cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public ClusterMonitor stopOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteClientHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.stopHardwareMonitoring());
      } catch (Exception e) {
        exceptions.add(e);
      }
    }

    if (!exceptions.isEmpty()) {
      RuntimeException re = new RuntimeException("Error stoping cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public void downloadTo(File location) {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        String metricsPath = workingPath.getPath().concat("/metrics");
        new RemoteFolder(ignite, hostname, null, metricsPath).downloadTo(new File(location, hostname));
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

  public void processMetrics(BiConsumer<String, TransportableFile> processor) {
    List<Exception> exceptions = new ArrayList<>();
    for (String hostname : hostnames) {
      try {
        String metricsPath = workingPath.getPath().concat("/metrics");
        RemoteFolder remoteFolder = new RemoteFolder(ignite, hostname, null, metricsPath);
        remoteFolder.list()
                .forEach(remoteFile -> {
                  TransportableFile transportable = remoteFile.toTransportableFile();
                  processor.accept(hostname, transportable);
                });
      } catch (Exception e) {
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
