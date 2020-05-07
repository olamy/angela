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

package org.terracotta.angela.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.angela.agent.Agent;
import org.terracotta.angela.client.config.ClientArrayConfigurationContext;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.client.config.MonitoringConfigurationContext;
import org.terracotta.angela.client.config.TmsConfigurationContext;
import org.terracotta.angela.client.config.TsaConfigurationContext;
import org.terracotta.angela.client.config.VoterConfigurationContext;
import org.terracotta.angela.client.remote.agent.RemoteAgentLauncher;
import org.terracotta.angela.common.cluster.Cluster;
import org.terracotta.angela.common.metrics.HardwareMetric;
import org.terracotta.angela.common.metrics.MonitoringCommand;
import org.terracotta.angela.common.net.DefaultPortAllocator;
import org.terracotta.angela.common.net.PortAllocator;
import org.terracotta.angela.common.topology.InstanceId;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.terracotta.angela.common.util.IpUtils.isLocal;

public class ClusterFactory implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(ClusterFactory.class);

  private static final String TSA = "tsa";
  private static final String TMS = "tms";
  private static final String CLIENT_ARRAY = "clientArray";
  private static final String MONITOR = "monitor";
  private static final String VOTER = "voter";
  private static final DateTimeFormatter PATH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmss");

  private final List<AutoCloseable> controllers = new ArrayList<>();
  private final String idPrefix;
  private final AtomicInteger instanceIndex;
  private final Map<String, Collection<InstanceId>> nodeToInstanceId = new HashMap<>();
  private final ConfigurationContext configurationContext;

  private Agent localAgent;
  private transient RemoteAgentLauncher remoteAgentLauncher;
  private InstanceId monitorInstanceId;

  private Map<String, String> agentsInstance = new HashMap<>();
  private final int ignitePort;
  private final PortAllocator portAllocator;

  public ClusterFactory(String idPrefix, ConfigurationContext configurationContext) {
    this(idPrefix, configurationContext, new DefaultPortAllocator());
  }

  public ClusterFactory(String idPrefix, ConfigurationContext configurationContext, PortAllocator portAllocator) {
    // Using UTC to have consistent layout even in case of timezone skew between client and server.
    this.idPrefix = idPrefix + "-" + LocalDateTime.now(ZoneId.of("UTC")).format(PATH_FORMAT);
    this.instanceIndex = new AtomicInteger();
    this.configurationContext = configurationContext;
    this.remoteAgentLauncher = configurationContext.remoting().buildRemoteAgentLauncher();
    this.portAllocator = portAllocator;
    try (PortAllocator.PortAllocation portAllocation = portAllocator.reserve(2)) {
      this.ignitePort = portAllocation.next();
    }
    this.localAgent = new Agent();
    this.localAgent.startCluster(Collections.singleton("localhost:" + ignitePort), "localhost:" + ignitePort, ignitePort);
    agentsInstance.put("localhost", "localhost:" + ignitePort);
  }

  private InstanceId init(String type, Collection<String> hostnames) {
    if (hostnames.isEmpty()) {
      throw new IllegalArgumentException("Cannot initialize with 0 server");
    }
    InstanceId instanceId = new InstanceId(idPrefix + "-" + instanceIndex.getAndIncrement(), type);
    for (String hostname : hostnames) {
      if (hostname == null) {
        throw new IllegalArgumentException("Cannot initialize with a null server name");
      }

      if (!isLocal(hostname) && !agentsInstance.containsKey(hostname)) {
        final String nodeName = hostname + ":" + ignitePort;

        StringBuilder addressesToDiscover = new StringBuilder();
        for (String agentAddress : agentsInstance.values()) {
          addressesToDiscover.append(agentAddress).append(",");
        }
        if (addressesToDiscover.length() > 0) {
          addressesToDiscover.deleteCharAt(addressesToDiscover.length() - 1);
        }
        remoteAgentLauncher.remoteStartAgentOn(hostname, nodeName, ignitePort, addressesToDiscover.toString());
        // start remote agent
        agentsInstance.put(hostname, nodeName);
      }
    }

    logger.info("Agents instance (size = {}) : ", agentsInstance.values().size());
    agentsInstance.values().forEach(value -> logger.info("- agent instance : {}", value));

    return instanceId;
  }

  public Cluster cluster() {
    if (localAgent.getIgnite() == null) {
      throw new IllegalStateException("No cluster component started");
    }
    return new Cluster(localAgent.getIgnite());
  }

  public Tsa tsa() {
    TsaConfigurationContext tsaConfigurationContext = configurationContext.tsa();
    InstanceId instanceId = init(TSA, tsaConfigurationContext.getTopology().getServersHostnames());

    Tsa tsa = new Tsa(localAgent.getIgnite(), ignitePort, portAllocator, instanceId, tsaConfigurationContext);
    controllers.add(tsa);
    return tsa;
  }

  public Tms tms() {
    TmsConfigurationContext tmsConfigurationContext = configurationContext.tms();
    InstanceId instanceId = init(TMS, Collections.singletonList(tmsConfigurationContext.getHostname()));

    Tms tms = new Tms(localAgent.getIgnite(), ignitePort, instanceId, tmsConfigurationContext);
    controllers.add(tms);
    return tms;
  }

  public Voter voter() {
    VoterConfigurationContext voterConfigurationContext = configurationContext.voter();
    InstanceId instanceId = init(VOTER, voterConfigurationContext.getHostNames());

    Voter voter = new Voter(localAgent.getIgnite(), ignitePort, instanceId, voterConfigurationContext);
    controllers.add(voter);
    return voter;
  }

  public ClientArray clientArray() {
    ClientArrayConfigurationContext clientArrayConfigurationContext = configurationContext.clientArray();
    init(CLIENT_ARRAY, clientArrayConfigurationContext.getClientArrayTopology().getClientHostnames());

    ClientArray clientArray = new ClientArray(localAgent.getIgnite(), ignitePort,
        () -> init(CLIENT_ARRAY, clientArrayConfigurationContext.getClientArrayTopology().getClientHostnames()), clientArrayConfigurationContext);
    controllers.add(clientArray);
    return clientArray;
  }

  public ClusterMonitor monitor() {
    MonitoringConfigurationContext monitoringConfigurationContext = configurationContext.monitoring();
    if (monitoringConfigurationContext == null) {
      throw new IllegalStateException("MonitoringConfigurationContext has not been registered");
    }

    Map<HardwareMetric, MonitoringCommand> commands = monitoringConfigurationContext.commands();
    Set<String> hostnames = configurationContext.allHostnames();

    if (monitorInstanceId == null) {
      monitorInstanceId = init(MONITOR, hostnames);
      ClusterMonitor clusterMonitor = new ClusterMonitor(this.localAgent.getIgnite(), ignitePort, monitorInstanceId, hostnames, commands);
      controllers.add(clusterMonitor);
      return clusterMonitor;
    } else {
      return new ClusterMonitor(localAgent.getIgnite(), ignitePort, monitorInstanceId, hostnames, commands);
    }
  }

  @Override
  public void close() throws IOException {
    List<Exception> exceptions = new ArrayList<>();

    for (AutoCloseable controller : controllers) {
      try {
        controller.close();
      } catch (Exception e) {
        e.printStackTrace();
        exceptions.add(e);
      }
    }
    controllers.clear();

    nodeToInstanceId.clear();

    monitorInstanceId = null;

    try {
      remoteAgentLauncher.close();
    } catch (Exception e) {
      e.printStackTrace();
      exceptions.add(e);
    }
    remoteAgentLauncher = null;

    if (localAgent != null) {
      try {
        logger.info("shutting down local agent");
        localAgent.close();
      } catch (Exception e) {
        e.printStackTrace();
        exceptions.add(e);
      }
      localAgent = null;
    }

    if (!exceptions.isEmpty()) {
      IOException ioException = new IOException("Error while closing down Cluster Factory prefixed with " + idPrefix);
      exceptions.forEach(ioException::addSuppressed);
      throw ioException;
    }
  }
}
