package com.terracottatech.qa.angela.common.clientconfig;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class ClientsConfig {

  private final Map<ClientSymbolicName, TerracottaClient> terracottaClients = new HashMap<>();

  private ClientsConfig() {}

  public static ClientsConfig newClientsConfig() {
    return new ClientsConfig();
  }

  public ClientsConfig client(String clientSymbolicName, String hostname) {
    final ClientSymbolicName key = new ClientSymbolicName(clientSymbolicName);
    if (this.terracottaClients.containsKey(key)) {
      throw new IllegalArgumentException("Client with ClientSymbolicName = " + clientSymbolicName + " already present in the ClientsConfig");
    }
    this.terracottaClients.put(key, new TerracottaClient(key, hostname));

    return this;
  }

  public Collection<  TerracottaClient> getTerracottaClients() {
    return Collections.unmodifiableCollection(this.terracottaClients.values());
  }

  public TerracottaClient getTerracottaClient(final ClientSymbolicName clientSymbolicName) {
    return this.terracottaClients.get(clientSymbolicName);
  }

  public ClientsConfig clientSerie(final int serieLength, final String hostname) {
    for (int i = 0; i < serieLength; i++) {
      String clientSymbolicName =  hostname + "." + i;
      client(clientSymbolicName, hostname);
    }
    return this;
  }
}
