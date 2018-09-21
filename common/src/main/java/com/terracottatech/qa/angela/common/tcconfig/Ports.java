package com.terracottatech.qa.angela.common.tcconfig;

/**
 * @author Tim Eck
 */
public class Ports {
  private final int tsaPort;
  private final int groupPort;
  private final int managementPort;
  private final int jmxPort;

  public Ports(int tsaPort, int groupPort, int managementPort, int jmxPort) {
    this.tsaPort = tsaPort;
    this.groupPort = groupPort;
    this.managementPort = managementPort;
    this.jmxPort = jmxPort;
  }

  public int getGroupPort() {
    return groupPort;
  }

  public int getManagementPort() {
    return managementPort;
  }

  public int getTsaPort() {
    return tsaPort;
  }

  public int getJmxPort() {
    return jmxPort;
  }

  @Override
  public String toString() {
    return "Ports{" +
           "tsaPort=" + tsaPort +
           ", groupPort=" + groupPort +
           ", managementPort=" + managementPort +
           ", jmxPort=" + jmxPort +
           '}';
  }
}