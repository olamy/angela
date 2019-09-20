package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;

public class ClusterTool {
  private final TerracottaServer terracottaServer;
  private final TerracottaCommandLineEnvironment tcEnv;
  private final Ignite ignite;
  private final InstanceId instanceId;

  ClusterTool(Ignite ignite, InstanceId instanceId, TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.terracottaServer = terracottaServer;
    this.tcEnv = tcEnv;
  }

  public ClusterToolExecutionResult executeCommand(String... arguments) {
    IgniteCallable<ClusterToolExecutionResult> callable = () -> Agent.controller.clusterTool(instanceId, terracottaServer, tcEnv, arguments);
    return IgniteClientHelper.executeRemotely(ignite, terracottaServer.getHostname(), callable);
  }

}
