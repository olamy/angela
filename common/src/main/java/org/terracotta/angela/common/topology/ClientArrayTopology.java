/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.topology;

import org.terracotta.angela.common.clientconfig.ClientArrayConfig;
import org.terracotta.angela.common.clientconfig.ClientId;
import org.terracotta.angela.common.distribution.Distribution;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Aurelien Broszniowski
 */

public class ClientArrayTopology {
  private final Distribution distribution;
  private final ClientArrayConfig clientArrayConfig;

  public ClientArrayTopology(ClientArrayConfig clientArrayConfig) {
    this(null, clientArrayConfig);
  }

  public ClientArrayTopology(Distribution distribution, ClientArrayConfig clientArrayConfig) {
    this.distribution = distribution;
    this.clientArrayConfig = clientArrayConfig;
  }

  public Collection<ClientId> getClientIds() {
    return clientArrayConfig.getHosts().entrySet().stream().map(entry -> new ClientId(entry.getKey(), entry.getValue())).collect(Collectors.toList());
  }

  public Collection<String> getClientHostnames() {
    return clientArrayConfig.getHosts().entrySet().stream()
        .map(clientSymbolicNameHostEntry -> clientSymbolicNameHostEntry.getValue().getHostname())
        .collect(Collectors.toList());
  }

  public Collection<ClientArrayConfig.Host> getClientHosts() {
    return clientArrayConfig.getHosts().values();
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
