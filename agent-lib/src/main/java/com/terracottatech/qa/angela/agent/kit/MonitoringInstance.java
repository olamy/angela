package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.metrics.HardwareMetricsCollector;
import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.io.File;

/**
 *
 * @author Aurelien Broszniowski
 */

public class MonitoringInstance {

  private final String workingKitInstallationPath;
  private final HardwareMetricsCollector hardwareMetricsCollector = new HardwareMetricsCollector();

  public MonitoringInstance(final InstanceId instanceId) {
    this.workingKitInstallationPath = Agent.ROOT_DIR + File.separator + "work" + File.separator + instanceId;
  }

  public void startHardwareMonitoring() {
    hardwareMetricsCollector.startMonitoring(new File(workingKitInstallationPath), HardwareMetricsCollector.TYPE.vmstat);
  }

  public void stopHardwareMonitoring() {
    hardwareMetricsCollector.stopMonitoring();
  }
}

