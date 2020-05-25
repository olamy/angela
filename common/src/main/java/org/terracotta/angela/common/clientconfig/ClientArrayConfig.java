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

package org.terracotta.angela.common.clientconfig;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Aurelien Broszniowski
 */

public class ClientArrayConfig {

  private final Map<ClientSymbolicName, Host> hosts = new HashMap<>();

  private ClientArrayConfig() {}

  public static ClientArrayConfig newClientArrayConfig() {
    return new ClientArrayConfig();
  }

  public ClientArrayConfig host(String hostname) {
    return host(hostname, hostname);
  }

  public ClientArrayConfig host(String hostname, int port) {
    return host(hostname, hostname, port);
  }

  public ClientArrayConfig host(String clientSymbolicName, String hostname) {
    return host(clientSymbolicName, hostname, 0);
  }

  public ClientArrayConfig host(String clientSymbolicName, String hostname, int port) {
    ClientSymbolicName key = new ClientSymbolicName(clientSymbolicName);
    if (this.hosts.containsKey(key)) {
      throw new IllegalArgumentException("Client with symbolic name '" + clientSymbolicName + "' already present in the client array config");
    }
    this.hosts.put(key, new Host(hostname, port));

    return this;
  }

  public Map<ClientSymbolicName, String> getHostnames() {
    return hosts.entrySet().stream()
        .map( entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getHostname()))
        .collect( Collectors.toMap( AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue) );

  }

  public Map<ClientSymbolicName, Host> getHosts() {
    return Collections.unmodifiableMap(hosts);
  }

  public ClientArrayConfig hostSerie(int serieLength, String hostname) {
    for (int i = 0; i < serieLength; i++) {
      String clientSymbolicName =  hostname + "-" + i;
      host(clientSymbolicName, hostname);
    }
    return this;
  }

  public ClientArrayConfig hostSerie(int serieLength, String hostname, int port) {
    for (int i = 0; i < serieLength; i++) {
      String clientSymbolicName =  hostname + "-" + i;
      host(clientSymbolicName, hostname, port);
    }
    return this;
  }

  public static class Host {
    private String hostname;
    private int port;

    public Host( String hostname, int port ) {
      this.hostname = hostname;
      this.port = port;
    }

    public String getHostname() {
      return hostname;
    }

    public int getPort() {
      return port;
    }
  }
}
