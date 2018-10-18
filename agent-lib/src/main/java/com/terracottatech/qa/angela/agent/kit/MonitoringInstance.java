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

  private final File workingKitInstallationPath;
  private final HardwareMetricsCollector hardwareMetricsCollector = new HardwareMetricsCollector();

  public MonitoringInstance(InstanceId instanceId) {
    this.workingKitInstallationPath = new File(Agent.WORK_DIR, instanceId.toString());
  }

  public void startHardwareMonitoring() {
    hardwareMetricsCollector.startMonitoring(workingKitInstallationPath, HardwareMetricsCollector.TYPE.vmstat);
  }

  public void stopHardwareMonitoring() {
    hardwareMetricsCollector.stopMonitoring();
  }
}

