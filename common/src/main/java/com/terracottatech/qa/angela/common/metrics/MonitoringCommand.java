package com.terracottatech.qa.angela.common.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MonitoringCommand {
  private static final String INTERVAL = "10";

  private final HardwareMetric hardwareMetric;
  private final List<String> command;

  public MonitoringCommand(HardwareMetric hardwareMetric) {
    this(hardwareMetric, getCommand(hardwareMetric));
  }

  public MonitoringCommand(HardwareMetric hardwareMetric, String... cmdArgs) {
    this(hardwareMetric, Arrays.asList(cmdArgs));
  }

  public MonitoringCommand(HardwareMetric hardwareMetric, List<String> cmdArgs) {
    this.hardwareMetric = hardwareMetric;
    this.command = new ArrayList<>(cmdArgs);
  }

  public String getCommandName() {
    return command.get(0);
  }

  public List<String> getCommand() {
    return Collections.unmodifiableList(command);
  }

  public HardwareMetric getHardwareMetric() {
    return hardwareMetric;
  }

  private static List<String> getCommand(HardwareMetric hardwareMetric) {
    switch (hardwareMetric) {
      case CPU:
        return Arrays.asList(
            "mpstat",
            "-P", // Specify the processors
            "ALL", // Specify ALL processors - duh!
            INTERVAL // Collection interval
        );
      case DISK:
        return Arrays.asList(
            "iostat",
            "-h", // Human-readable output
            "-d", // Record stats for disks
            INTERVAL // Collection interval
        );
      case MEMORY:
        return Arrays.asList(
            "free",
            "-h", // Human-readable output
            "-s", // Specify collection time in seconds
            INTERVAL // Collection interval
        );
      case NETWORK:
        return Arrays.asList(
            "sar",
            "-n", // Specify network statistics
            "DEV", // Observe traffic on interfaces
            INTERVAL // Collection interval
        );
      default:
        throw new IllegalArgumentException("Unrecognized HardwareMetric: " + hardwareMetric);
    }
  }
}
