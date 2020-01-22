package com.terracottatech.qa.angela.client.config;

import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.metrics.MonitoringCommand;

import java.util.Map;

public interface MonitoringConfigurationContext {
  Map<HardwareMetric, MonitoringCommand> commands();
}
