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

import org.terracotta.angela.common.util.IpUtils;

public class TmsConfig {

  private final String hostname;
  private final int tmsPort;

  public TmsConfig(String hostname, int tmsPort) {
    this.hostname = hostname;
    this.tmsPort = tmsPort;
  }

  public String getHostname() {
    return hostname;
  }

  public String getIp() {
    return IpUtils.getHostAddress(hostname);
  }

  public int getTmsPort() {
    return tmsPort;
  }

  public static TmsConfig noTms() {
    return null;
  }

  public static TmsConfig withTms(String hostname, int tmsPort) {
    return new TmsConfig(hostname, tmsPort);
  }

  public static TmsConfig hostnameAndPort(String hostname, int tmsPort) {
    return new TmsConfig(hostname, tmsPort);
  }
}
