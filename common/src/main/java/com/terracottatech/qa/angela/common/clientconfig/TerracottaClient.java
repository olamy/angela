package com.terracottatech.qa.angela.common.clientconfig;

/**
 * @author Aurelien Broszniowski
 */

public class TerracottaClient {

  private final ClientSymbolicName clientSymbolicName;
  private final String hostname;


  public TerracottaClient(final ClientSymbolicName clientSymbolicName, final String hostname) {
    this.clientSymbolicName = clientSymbolicName;
    this.hostname = hostname;
  }

  public String getHostname() {
    return hostname;
  }

  public ClientSymbolicName getClientSymbolicName() {
    return clientSymbolicName;
  }

  @Override
  public String toString() {
    return "TerracottaClient{" +
           "clientSymbolicName=" + clientSymbolicName +
           ", hostname='" + hostname + '\'' +
           '}';
  }
}
