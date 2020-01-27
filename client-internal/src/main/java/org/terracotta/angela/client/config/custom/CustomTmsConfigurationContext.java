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

package org.terracotta.angela.client.config.custom;

import org.terracotta.angela.client.config.TmsConfigurationContext;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.tms.security.config.TmsServerSecurityConfig;

public class CustomTmsConfigurationContext implements TmsConfigurationContext {
  private Distribution distribution;
  private License license;
  private String hostname;
  private TmsServerSecurityConfig securityConfig;
  private TerracottaCommandLineEnvironment terracottaCommandLineEnvironment = TerracottaCommandLineEnvironment.DEFAULT;

  protected CustomTmsConfigurationContext() {
  }

  @Override
  public Distribution getDistribution() {
    return distribution;
  }

  public CustomTmsConfigurationContext distribution(Distribution distribution) {
    this.distribution = distribution;
    return this;
  }

  @Override
  public License getLicense() {
    return license;
  }

  public CustomTmsConfigurationContext license(License license) {
    this.license = license;
    return this;
  }

  @Override
  public String getHostname() {
    return hostname;
  }

  public CustomTmsConfigurationContext hostname(String serverName) {
    this.hostname = serverName;
    return this;
  }

  @Override
  public TmsServerSecurityConfig getSecurityConfig() {
    return securityConfig;
  }

  public CustomTmsConfigurationContext securityConfig(TmsServerSecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
    return this;
  }

  @Override
  public TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment() {
    return terracottaCommandLineEnvironment;
  }

  public CustomTmsConfigurationContext terracottaCommandLineEnvironment(TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.terracottaCommandLineEnvironment = terracottaCommandLineEnvironment;
    return this;
  }
}
