package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.RemotingConfigurationContext;
import com.terracottatech.qa.angela.client.remote.agent.RemoteAgentLauncher;

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
