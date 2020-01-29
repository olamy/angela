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

package org.terracotta.angela.client.config.custom;

import org.terracotta.angela.client.config.MonitoringConfigurationContext;
import org.terracotta.angela.common.metrics.HardwareMetric;
import org.terracotta.angela.common.metrics.MonitoringCommand;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class CustomMonitoringConfigurationContext implements MonitoringConfigurationContext {
  private final Map<HardwareMetric, MonitoringCommand> commands = new HashMap<>();

  @Override
  public Map<HardwareMetric, MonitoringCommand> commands() {
    return Collections.unmodifiableMap(commands);
  }

  public CustomMonitoringConfigurationContext commands(EnumSet<HardwareMetric> hardwareMetrics) {
    for (HardwareMetric hardwareMetric : hardwareMetrics) {
      commands.put(hardwareMetric, hardwareMetric.getDefaultMonitoringCommand());
    }
    return this;
  }

  public CustomMonitoringConfigurationContext command(HardwareMetric hardwareMetric, MonitoringCommand monitoringCommand) {
    commands.put(hardwareMetric, monitoringCommand);
    return this;
  }

}
