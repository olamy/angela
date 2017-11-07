package com.terracottatech.qa.angela.tcconfig;

import java.io.Serializable;

/**
 * @author Tim Eck
 */
public class Ports implements Serializable {
  private int tsaPort;
  private int groupPort;
  private int managementPort;
  private int jmxPort;

  public Ports() {
  }

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
}