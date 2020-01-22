package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.ToolExecutionResult;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;

public class Jcmd {

  private final TerracottaCommandLineEnvironment tcEnv;
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final TerracottaServer terracottaServer;
  private final Client client;

  Jcmd(Ignite ignite, InstanceId instanceId, TerracottaServer terracottaServer, TerracottaCommandLineEnvironment tcEnv) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.terracottaServer = terracottaServer;
    this.client = null;
    this.tcEnv = tcEnv;
  }

  Jcmd(Ignite ignite, InstanceId instanceId, Client client, TerracottaCommandLineEnvironment tcEnv) {
    this.ignite = ignite;
    this.instanceId = instanceId;
    this.terracottaServer = null;
    this.client = client;
    this.tcEnv = tcEnv;
  }

  /**
   * Execute jcmd on the target JVM. This basically creates and execute a command line looking like the following:
   * ${JAVA_HOME}/bin/jcmd &lt;the JVM's PID&gt; &lt;arguments&gt;
   * @param arguments The arguments to pass to jcmd after the PID.
   * @return A representation of the jcmd execution
   */
  public ToolExecutionResult executeCommand(String... arguments) {
    String hostname;
    IgniteCallable<ToolExecutionResult> callable;
    if (terracottaServer != null) {
      hostname = terracottaServer.getHostname();
      callable = () -> Agent.controller.serverJcmd(instanceId, terracottaServer, tcEnv, arguments);
    } else if (client != null) {
      hostname = client.getHostname();
      callable = () -> Agent.controller.clientJcmd(instanceId, client.getPid(), tcEnv, arguments);
    } else {
      throw new AssertionError();
    }

    return IgniteClientHelper.executeRemotely(ignite, hostname, callable);
  }

}
