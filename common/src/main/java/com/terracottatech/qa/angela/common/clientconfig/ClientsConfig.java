package com.terracottatech.qa.angela.common.clientconfig;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class ClientsConfig {

  Map<ClientSymbolicName, TerracottaClient> terracottaClients = new HashMap<>();

  public ClientsConfig(final String hostname) {
    final ClientSymbolicName clientSymbolicName = new ClientSymbolicName(hostname);
    this.terracottaClients.put(clientSymbolicName, new TerracottaClient(clientSymbolicName, hostname));
  }

  public ClientsConfig(final String... hostnames) {
    for (String hostname : hostnames) {
      final ClientSymbolicName clientSymbolicName = new ClientSymbolicName(hostname);
      if (this.terracottaClients.containsKey(clientSymbolicName)) {
        // TODO : what the hell did this mean?
        throw new IllegalArgumentException("If you want to add multiple clients on the same hostname, you need to use");
      }
      this.terracottaClients.put(clientSymbolicName, new TerracottaClient(clientSymbolicName, hostname));
    }
  }

  private ClientsConfig() {}

  public static ClientsConfig newClientsConfig() {
    return new ClientsConfig();
  }

  public ClientsConfig client(ClientSymbolicName clientSymbolicName, String hostname) {
    if (this.terracottaClients.containsKey(clientSymbolicName)) {
      throw new IllegalArgumentException("Client with ClientSymbolicName = " + clientSymbolicName + " already present in the ClientsConfig");
    }
    this.terracottaClients.put(clientSymbolicName, new TerracottaClient(clientSymbolicName, hostname));

    return this;
  }

  public Map<ClientSymbolicName, TerracottaClient> getTerracottaClients() {
    return this.terracottaClients;
  }

  public TerracottaClient getTerracottaClient(final ClientSymbolicName clientSymbolicName) {
    return this.terracottaClients.get(clientSymbolicName);
  }
}
