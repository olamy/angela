package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.MonitoringConfigurationContext;
import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.metrics.MonitoringCommand;

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
