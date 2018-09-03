package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.clientconfig.ClientSymbolicName;
import com.terracottatech.qa.angela.common.clientconfig.ClientsConfig;
import com.terracottatech.qa.angela.common.clientconfig.TerracottaClient;
import com.terracottatech.qa.angela.common.distribution.Distribution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class ClientTopology {
  private final Distribution distribution;
  private final ClientsConfig clientsConfig;

  public ClientTopology(final Distribution distribution, final ClientsConfig clientsConfig) {
    this.distribution = distribution;
    this.clientsConfig = clientsConfig;
  }

  public Map<ClientSymbolicName, TerracottaClient> getClients() {
    return clientsConfig.getTerracottaClients();
  }

  public TerracottaClient getClient(ClientSymbolicName clientSymbolicName) {
    return clientsConfig.getTerracottaClient(clientSymbolicName);
  }

  public LicenseType getLicenseType() {
    return distribution.getLicenseType();
  }

  @Override
  public String toString() {
    return "ClientTopology{" +
           "distribution=" + distribution +
           ", clientsConfig=" + clientsConfig +
           '}';
  }

  public Distribution getDistribution() {
    return distribution;
  }

  public Collection<String> getClientsHostnames() {
    List<String> hostnames = new ArrayList<>();
    final Map<ClientSymbolicName, TerracottaClient> terracottaClients = clientsConfig.getTerracottaClients();
    for (TerracottaClient terracottaClient : terracottaClients.values()) {
      hostnames.add(terracottaClient.getHostname());
    }
    return hostnames;
  }
}