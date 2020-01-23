package com.terracottatech.qa.angela.common.util;

import java.util.Objects;

import static com.terracottatech.qa.angela.common.util.HostAndIpValidator.isValidIPv6;
import static java.util.Objects.requireNonNull;

public class HostPort {
  private final String hostname;
  private final int port;

  public HostPort(String hostname, int port) {
    this.hostname = requireNonNull(hostname);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HostPort hostPort = (HostPort) o;
    return port == hostPort.port &&
        hostname.equals(hostPort.hostname);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hostname, port);
  }
}
