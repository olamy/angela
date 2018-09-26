package com.terracottatech.qa.angela.common.clientconfig;

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class ClientId {

  private final ClientSymbolicName symbolicName;
  private final String hostname;

  public ClientId(ClientSymbolicName symbolicName, String hostname) {
    this.symbolicName = Objects.requireNonNull(symbolicName);
    this.hostname = Objects.requireNonNull(hostname);
  }

  public ClientSymbolicName getSymbolicName() {
    return symbolicName;
  }

  public String getHostname() {
    return hostname;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientId clientId = (ClientId) o;
    return Objects.equals(symbolicName, clientId.symbolicName) &&
        Objects.equals(hostname, clientId.hostname);
  }

  @Override
  public int hashCode() {
    return Objects.hash(symbolicName, hostname);
  }

  @Override
  public String toString() {
    return "ClientData{" +
           "symbolicName=" + symbolicName +
           ", hostname='" + hostname + '\'' +
           '}';
  }
}
