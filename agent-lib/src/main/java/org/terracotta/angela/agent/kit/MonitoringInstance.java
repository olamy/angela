/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.agent.kit;

import org.terracotta.angela.common.metrics.HardwareMetric;
import org.terracotta.angela.common.metrics.HardwareMetricsCollector;
import org.terracotta.angela.common.metrics.MonitoringCommand;

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

