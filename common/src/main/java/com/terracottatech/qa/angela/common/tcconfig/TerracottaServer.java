package com.terracottatech.qa.angela.common.tcconfig;

import com.terracottatech.qa.angela.common.util.IpUtils;

/**
 * Logical definition of a Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServer {

  private final ServerSymbolicName serverSymbolicName;
  private final String hostname;
  private final Ports ports;

  public TerracottaServer(String symbolicName, String hostname, int tsaPort, int tsaGroupPort, int managementPort, int jmxPort) {
    this.serverSymbolicName = new ServerSymbolicName(symbolicName);
    this.hostname = hostname;
    this.ports = new Ports(tsaPort, tsaGroupPort, managementPort, jmxPort);
  }

  public String getHostname() {
    return hostname;
  }

  public String getIp() {
    return IpUtils.getHostAddress(hostname);
  }

  public Ports getPorts() {
    return ports;
  }

  public ServerSymbolicName getServerSymbolicName() {
    return serverSymbolicName;
  }

  @Override
  public String toString() {
    return "TerracottaServer{" +
        "serverSymbolicName=" + serverSymbolicName +
        ", hostname='" + hostname + '\'' +
        ", ports=" + ports +
        '}';
  }
}
