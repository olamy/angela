package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.metrics.HardwareMetricsCollector;

import java.io.File;

/**
 *
 * @author Aurelien Broszniowski
 */

public class MonitoringInstance {

  private final File workingPath;
  private final HardwareMetricsCollector hardwareMetricsCollector = new HardwareMetricsCollector();

  public MonitoringInstance(File workingPath) {
    this.workingPath = workingPath;
  }

  public void startHardwareMonitoring() {
    hardwareMetricsCollector.startMonitoring(workingPath, HardwareMetricsCollector.TYPE.vmstat);
  }

  public void stopHardwareMonitoring() {
    hardwareMetricsCollector.stopMonitoring();
  }
}

