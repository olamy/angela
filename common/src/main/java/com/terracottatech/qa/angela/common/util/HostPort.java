package com.terracottatech.qa.angela.common.util;

import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv6;

public class HostPort {
  private final String hostname;
  private final int port;

  public HostPort(String hostname, int port) {
    this.hostname = hostname;
    this.port = port;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  public String getHostPort() {
    return encloseInBracketsIfIpv6(hostname) + ":" + port;
  }

  private String encloseInBracketsIfIpv6(String hostname) {
    if (hostname != null && isValidIPv6(hostname, false)) {
      return "[" + hostname + "]";
    }
    return hostname;
  }
}
