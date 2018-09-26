package com.terracottatech.qa.angela.common.topology;

import com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig;
import com.terracottatech.qa.angela.common.clientconfig.ClientId;
import com.terracottatech.qa.angela.common.distribution.Distribution;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Aurelien Broszniowski
 */

public class ClientArrayTopology {
  private final Distribution distribution;
  private final ClientArrayConfig clientArrayConfig;

  public ClientArrayTopology(Distribution distribution, ClientArrayConfig clientArrayConfig) {
    this.distribution = distribution;
    this.clientArrayConfig = clientArrayConfig;
  }

  public Collection<ClientId> getClientIds() {
    return clientArrayConfig.getHosts().entrySet().stream().map(entry -> new ClientId(entry.getKey(), entry.getValue())).collect(Collectors.toList());
  }

  public Collection<String> getClientHostnames() {
    return clientArrayConfig.getHosts().entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toList());
  }

  public Distribution getDistribution() {
    return distribution;
  }

  @Override
  public String toString() {
    return "ClientArrayTopology{" +
           "distribution=" + distribution +
           ", clientsConfig=" + clientArrayConfig +
           '}';
  }

}