package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.logger.NullLogger;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClusterFactory implements AutoCloseable {
  private final static Logger LOGGER = LoggerFactory.getLogger(ClusterFactory.class);

  private final List<AutoCloseable> controllers = new ArrayList<>();
  private final String idPrefix;
  private final Map<String, InstanceId> nodeToInstanceId = new HashMap<>();
  private Ignite ignite;
  private boolean localhostOnly;
  private Agent.Node localhostAgent;

  public ClusterFactory(String idPrefix) {
    this.idPrefix = idPrefix;
  }

  private InstanceId init(Collection<String> targetServerNames) {
    if (targetServerNames.isEmpty()) {
      throw new IllegalArgumentException("Cannot initialize with 0 server");
    }

    InstanceId instanceId = new InstanceId(idPrefix);
    for (String targetServerName : targetServerNames) {
      if (targetServerName == null) {
        throw new IllegalArgumentException("Cannot initialize with a null server name");
      }
      nodeToInstanceId.put(targetServerName, instanceId);
    }

    if (ignite != null) {
      return instanceId;
    }

    if (isLocalhostOnly(targetServerNames)) {
      LOGGER.info("spawning localhost agent");
      localhostAgent = new Agent.Node("localhost");
      localhostAgent.init();
      localhostOnly = true;
    }

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    if (localhostOnly) {
      ipFinder.setAddresses(targetServerNames.stream().map(targetServerName -> targetServerName + ":40000").collect(Collectors.toList()));
    } else {
      ipFinder.setAddresses(targetServerNames);
    }
    spi.setJoinTimeout(10000);
    spi.setIpFinder(ipFinder);

    IgniteConfiguration cfg = new IgniteConfiguration();
    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    boolean enableLogging = Boolean.getBoolean(Agent.IGNITE_LOGGING_SYSPROP_NAME);
    cfg.setGridLogger(enableLogging ? new Slf4jLogger() : new NullLogger());
    cfg.setIgniteInstanceName("Instance@" + instanceId);
    cfg.setMetricsLogFrequency(0);

    try {
      this.ignite = Ignition.start(cfg);
    } catch (IgniteException e) {
      throw new RuntimeException("Cannot start angela; error connecting to agents : " + targetServerNames, e);
    }

    return instanceId;
  }

  private static boolean isLocalhostOnly(Collection<String> targetServerNames) {
    for (String targetServerName : targetServerNames) {
      if (!targetServerName.equals("localhost")) {
        return false;
      }
    }
    return true;
  }

  public Tsa tsa(Topology topology) {
    InstanceId instanceId = init(topology.getServersHostnames());

    Tsa tsa = new Tsa(ignite, instanceId, topology);
    controllers.add(tsa);
    return tsa;
  }

  public Tsa tsa(Topology topology, License license) {
    InstanceId instanceId = init(topology.getServersHostnames());

    Tsa tsa = new Tsa(ignite, instanceId, topology, license);
    controllers.add(tsa);
    return tsa;
  }

  public Client client(String nodeName) {
    if (!"localhost".equals(nodeName) && localhostOnly) {
      throw new IllegalArgumentException("localhost agent started, connection to remote agent '" + nodeName + "' is not possible");
    }

    InstanceId instanceId = init(Collections.singleton(nodeName));

    Client client = new Client(ignite, instanceId, nodeName, localhostOnly);
    controllers.add(client);
    return client;
  }

  public Tms tms(Distribution distribution, License license, String hostname) {
    return tms(distribution, license, hostname, null);
  }

  public Tms tms(Distribution distribution, License license, String hostname, TmsServerSecurityConfig securityConfig) {
    InstanceId instanceId = init(Collections.singletonList(hostname));

    Tms tms = new Tms(ignite, instanceId, license, hostname, distribution, securityConfig);
    controllers.add(tms);
    return tms;
  }

  @Override
  public void close() throws Exception {
    for (AutoCloseable controller : controllers) {
      controller.close();
    }
    controllers.clear();

    for (String nodeName : nodeToInstanceId.keySet()) {
      IgniteHelper.checkAgentHealth(ignite, nodeName);
      ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
      InstanceId instanceId = nodeToInstanceId.get(nodeName);
      ignite.compute(location).broadcast((IgniteRunnable) () -> Agent.CONTROLLER.cleanup(instanceId));
    }
    nodeToInstanceId.clear();

    if (ignite != null) {
      ignite.close();
      ignite = null;
    }

    if (localhostAgent != null) {
      LOGGER.info("shutting down localhost agent");
      localhostAgent.shutdown();
      localhostAgent = null;
    }
  }

}
