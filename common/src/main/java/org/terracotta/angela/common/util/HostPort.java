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

package org.terracotta.angela.common.util;

import java.util.Objects;

import static org.terracotta.angela.common.util.HostAndIpValidator.isValidIPv6;
import static java.util.Objects.requireNonNull;

public class HostPort {
  private final String hostname;
  private final int port;

  public HostPort(String hostname, int port) {
    this.hostname = requireNonNull(hostname);
    this.port = port;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  public String getHostPort() {
    return encloseInBracketsIfIpv6(hostname) + ":" + port;
  }

  private String encloseInBracketsIfIpv6(String hostname) {
    if (hostname != null && HostAndIpValidator.isValidIPv6(hostname, false)) {
      return "[" + hostname + "]";
    }
    return hostname;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HostPort hostPort = (HostPort) o;
    return port == hostPort.port &&
        hostname.equals(hostPort.hostname);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hostname, port);
  }
}
