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

import org.terracotta.angela.client.config.ClientArrayConfigurationContext;
import org.terracotta.angela.common.TerracottaCommandLineEnvironment;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.topology.ClientArrayTopology;

public class CustomClientArrayConfigurationContext implements ClientArrayConfigurationContext {
  private ClientArrayTopology clientArrayTopology;
  private License license;
  private TerracottaCommandLineEnvironment terracottaCommandLineEnvironment = TerracottaCommandLineEnvironment.DEFAULT;

  protected CustomClientArrayConfigurationContext() {
  }

  @Override
  public ClientArrayTopology getClientArrayTopology() {
    return clientArrayTopology;
  }

  public CustomClientArrayConfigurationContext clientArrayTopology(ClientArrayTopology clientArrayTopology) {
    this.clientArrayTopology = clientArrayTopology;
    return this;
  }

  @Override
  public License getLicense() {
    return license;
  }

  public CustomClientArrayConfigurationContext license(License license) {
    this.license = license;
    return this;
  }

  @Override
  public TerracottaCommandLineEnvironment getTerracottaCommandLineEnvironment() {
    return terracottaCommandLineEnvironment;
  }

  public CustomClientArrayConfigurationContext terracottaCommandLineEnvironment(TerracottaCommandLineEnvironment terracottaCommandLineEnvironment) {
    this.terracottaCommandLineEnvironment = terracottaCommandLineEnvironment;
    return this;
  }
}
