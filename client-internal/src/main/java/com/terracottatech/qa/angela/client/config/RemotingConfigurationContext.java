package com.terracottatech.qa.angela.client.config;

import com.terracottatech.qa.angela.client.remote.agent.RemoteAgentLauncher;

public interface RemotingConfigurationContext {
  RemoteAgentLauncher buildRemoteAgentLauncher();
}
