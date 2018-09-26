package com.terracottatech.qa.angela.common.clientconfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class ClientArrayConfig {

  private final Map<ClientSymbolicName, String> hosts = new HashMap<>();

  private ClientArrayConfig() {}

  public static ClientArrayConfig newClientArrayConfig() {
    return new ClientArrayConfig();
  }

  public ClientArrayConfig host(String hostname) {
    return host(hostname, hostname);
  }

  public ClientArrayConfig host(String clientSymbolicName, String hostname) {
    ClientSymbolicName key = new ClientSymbolicName(clientSymbolicName);
    if (this.hosts.containsKey(key)) {
      throw new IllegalArgumentException("Client with symbolic name '" + clientSymbolicName + "' already present in the client array config");
    }
    this.hosts.put(key, hostname);

    return this;
  }

  public Map<ClientSymbolicName, String> getHosts() {
    return Collections.unmodifiableMap(hosts);
  }

  public ClientArrayConfig hostSerie(int serieLength, String hostname) {
    for (int i = 0; i < serieLength; i++) {
      String clientSymbolicName =  hostname + "-" + i;
      host(clientSymbolicName, hostname);
    }
    return this;
  }
}
