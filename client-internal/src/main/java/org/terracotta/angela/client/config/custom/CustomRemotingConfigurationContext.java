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

import org.terracotta.angela.client.remote.agent.RemoteAgentLauncher;
import org.terracotta.angela.client.config.RemotingConfigurationContext;

import java.util.function.Supplier;

public class CustomRemotingConfigurationContext implements RemotingConfigurationContext {
  private Supplier<RemoteAgentLauncher> remoteAgentLauncherSupplier;

  @Override
  public RemoteAgentLauncher buildRemoteAgentLauncher() {
    return remoteAgentLauncherSupplier.get();
  }

  public CustomRemotingConfigurationContext remoteAgentLauncherSupplier(Supplier<RemoteAgentLauncher> remoteAgentLauncherSupplier) {
    this.remoteAgentLauncherSupplier = remoteAgentLauncherSupplier;
    return this;
  }
}
