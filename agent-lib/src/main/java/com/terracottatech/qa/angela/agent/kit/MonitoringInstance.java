package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.metrics.HardwareMetricsCollector;
import com.terracottatech.qa.angela.common.metrics.MonitoringCommand;

import java.io.File;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class MonitoringInstance {

  private final File workingPath;
  private final HardwareMetricsCollector hardwareMetricsCollector = new HardwareMetricsCollector();

  public MonitoringInstance(File workingPath) {
    this.workingPath = workingPath;
  }

  public void startHardwareMonitoring(Map<HardwareMetric, MonitoringCommand> commands) {
    hardwareMetricsCollector.startMonitoring(workingPath, commands);
  }

  public void stopHardwareMonitoring() {
    hardwareMetricsCollector.stopMonitoring();
  }

  public boolean isMonitoringRunning(HardwareMetric hardwareMetric) {
    return hardwareMetricsCollector.isMonitoringRunning(hardwareMetric);
  }
}

