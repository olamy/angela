package com.terracottatech.qa.angela.common.metrics;

public class MonitoringCommand {
  private final HardwareMetric hardwareMetric;
  private static final String INTERVAL = "10";

  public MonitoringCommand(HardwareMetric hardwareMetric) {
    this.hardwareMetric = hardwareMetric;
  }

  public String getCommandName() {
    return getCommand()[0];
  }

  public String[] getCommand() {
    switch (hardwareMetric) {
      case CPU:
        return getCpuMonitoringCommand();
      case DISK:
        return getDiskMonitoringCommand();
      case MEMORY:
        return getMemoryMonitoringCommand();
      case NETWORK:
        return getNetworkMonitoringCommand();
      default:
        throw new IllegalArgumentException("Unrecognized HardwareMetric: " + hardwareMetric);
    }
  }

  public String[] getCpuMonitoringCommand() {
    return new String[]{
        "mpstat",
        "-P", // Specify the processors
        "ALL", // Specify ALL processors - duh!
        INTERVAL // Collection interval
    };
  }

  public String[] getDiskMonitoringCommand() {
    return new String[]{
        "iostat",
        "-h", // Human-readable output
        "-d", // Record stats for disks
        INTERVAL // Collection interval
    };
  }

  public String[] getMemoryMonitoringCommand() {
    return new String[]{
        "free",
        "-h", // Human-readable output
        "-s", // Specify collection time in seconds
        INTERVAL // Collection interval
    };
  }

  public String[] getNetworkMonitoringCommand() {
    return new String[]{
        "sar",
        "-n", // Specify network statistics
        "DEV", // Observe traffic on interfaces
        INTERVAL // Collection interval
    };
  }
}
