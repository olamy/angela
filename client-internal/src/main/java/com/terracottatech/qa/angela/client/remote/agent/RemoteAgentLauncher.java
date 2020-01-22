package com.terracottatech.qa.angela.client.remote.agent;

import java.util.Collection;

public interface RemoteAgentLauncher extends AutoCloseable {
  void remoteStartAgentOn(String targetServerName, Collection<String> nodesToJoin);
}
