package com.terracottatech.qa.angela.common.tcconfig;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Logical definition of a Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServer implements Serializable {

  private ServerSymbolicName serverSymbolicName;
  private String hostname;
  private Ports ports;

  public TerracottaServer(String symbolicName, String hostname, int tsaPort, int tsaGroupPort, int managementPort, int jmxPort) {
    this.serverSymbolicName = new ServerSymbolicName(symbolicName);
    this.hostname = hostname;
    this.ports = new Ports(tsaPort, tsaGroupPort, managementPort, jmxPort);
  }

  public String getHostname() {
    return hostname;
  }

  public String getIp() throws UnknownHostException {
    InetAddress address = InetAddress.getByName(hostname);
    return address.getHostAddress();
  }

  public Ports getPorts() {
    return ports;
  }

  public ServerSymbolicName getServerSymbolicName() {
    return serverSymbolicName;
  }

  public void setPorts(final Ports ports) {
    this.ports = ports;
  }
}
