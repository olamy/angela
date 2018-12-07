package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.filesystem.TransportableFile;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.metrics.HardwareMetricsCollector;
import com.terracottatech.qa.angela.common.metrics.MonitoringCommand;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterMonitor implements AutoCloseable {

  private final Ignite ignite;
  private final File workingPath;
  private final Set<String> hostnames;
  private final Map<HardwareMetric, MonitoringCommand> commands;
  private boolean closed = false;

  ClusterMonitor(Ignite ignite, InstanceId instanceId, Set<String> hostnames, Map<HardwareMetric, MonitoringCommand> commands) {
    this.ignite = ignite;
    this.workingPath = new File(Agent.WORK_DIR, instanceId.toString());
    this.hostnames = hostnames;
    this.commands = commands;
  }

  public ClusterMonitor startOnAll() {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        IgniteClientHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.startHardwareMonitoring(workingPath.getPath(), commands));
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
      RuntimeException re = new RuntimeException("Error stopping cluster monitors");
      exceptions.forEach(re::addSuppressed);
      throw re;
    }
    return this;
  }

  public void downloadTo(File location) {
    List<Exception> exceptions = new ArrayList<>();

    for (String hostname : hostnames) {
      try {
        Path metricsPath = workingPath.toPath().resolve(HardwareMetricsCollector.METRICS_DIRECTORY);
        new RemoteFolder(ignite, hostname, null, metricsPath.toString()).downloadTo(new File(location, hostname));
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
        Path metricsPath = workingPath.toPath().resolve(HardwareMetricsCollector.METRICS_DIRECTORY);
        RemoteFolder remoteFolder = new RemoteFolder(ignite, hostname, null, metricsPath.toString());
        remoteFolder.list().forEach(remoteFile -> processor.accept(hostname, remoteFile.toTransportableFile()));
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

  public boolean isMonitoringRunning(HardwareMetric metric) {
    for (String hostname : hostnames) {
      boolean running = IgniteClientHelper.executeRemotely(ignite, hostname, () -> Agent.CONTROLLER.isMonitoringRunning(metric));
      if (!running) {
        return false;
      }
    }
    return true;
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
