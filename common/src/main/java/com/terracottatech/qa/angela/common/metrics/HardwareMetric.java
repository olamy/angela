package com.terracottatech.qa.angela.common.metrics;

public enum HardwareMetric {

    CPU(new MonitoringCommand("mpstat",
        "-P", // Specify the processors
        "ALL", // Specify ALL processors - duh!
        "10")),
    DISK(new MonitoringCommand( "iostat",
        "-h", // Human-readable output
        "-d", // Record stats for disks
        "10")),
    MEMORY(new MonitoringCommand("free",
        "-h", // Human-readable output
        "-s", // Specify collection time in seconds
        "10" )),
    NETWORK(new MonitoringCommand("sar",
        "-n", // Specify network statistics
        "DEV", // Observe traffic on interfaces
        "10")),
    ;

    private final MonitoringCommand defaultMonitoringCommand;

    HardwareMetric(MonitoringCommand defaultMonitoringCommand) {
        this.defaultMonitoringCommand = defaultMonitoringCommand;
    }

    public MonitoringCommand getDefaultMonitoringCommand() {
        return defaultMonitoringCommand;
    }

}
