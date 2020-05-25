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

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class ClientId {

  private final ClientSymbolicName symbolicName;
  private final ClientArrayConfig.Host host;

  public ClientId(ClientSymbolicName symbolicName, ClientArrayConfig.Host host) {
    this.symbolicName = Objects.requireNonNull(symbolicName);
    this.host = Objects.requireNonNull(host);
  }

  public ClientSymbolicName getSymbolicName() {
    return symbolicName;
  }

  public String getHostname() {
    return host.getHostname();
  }

  public ClientArrayConfig.Host getHost()
  {
    return host;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientId clientId = (ClientId) o;
    return Objects.equals(symbolicName, clientId.symbolicName) &&
           Objects.equals(host.getHostname(), clientId.host.getHostname()) &&
           Objects.equals(host.getPort(), clientId.host.getPort());
  }

  @Override
  public int hashCode() {
    return Objects.hash(symbolicName, host.getHostname(), host.getPort());
  }

  @Override
  public String toString() {
    return "ClientData{" +
           "symbolicName=" + symbolicName +
           ", hostname=" + host.getHostname() +
           ", port='" + host.getPort() + '\'' +
           '}';
  }
}
