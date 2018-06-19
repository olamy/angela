package com.terracottatech.qa.angela.client.remote.agent;

public interface RemoteAgentLauncher extends AutoCloseable {
  void remoteStartAgentOn(String targetServerName);
}
