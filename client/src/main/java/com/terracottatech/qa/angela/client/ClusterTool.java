package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;

public class ClusterTool {
  private final TerracottaServer terracottaServer;
  private final Ignite ignite;
  private final InstanceId instanceId;

  ClusterTool(Ignite ignite, InstanceId instanceId, TerracottaServer terracottaServer) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.terracottaServer = terracottaServer;
  }

  public ClusterToolExecutionResult executeCommand(String... arguments) {
    return executeRemotely(terracottaServer, () -> Agent.CONTROLLER.clusterTool(instanceId, terracottaServer, arguments)).get();
  }

  private <R> IgniteFuture<R> executeRemotely(final TerracottaServer hostname, final IgniteCallable<R> callable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname.getHostname());
    return ignite.compute(location).callAsync(callable);
  }

}
