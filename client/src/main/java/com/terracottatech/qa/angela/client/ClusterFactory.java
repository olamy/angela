package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ClusterFactory implements AutoCloseable {
  private final static Logger logger = LoggerFactory.getLogger(ClusterFactory.class);

  private final InstanceId instanceId;
  private final List<AutoCloseable> controllers = new ArrayList<>();
  private final Set<String> nodesToCleanup = new HashSet<>();
  private Ignite ignite;
  private Agent.Node localhostAgent;
  private Optional<URI> clusterURI;

  public ClusterFactory(String idPrefix) {
    this.instanceId = new InstanceId(idPrefix);
  }

  private void init(Collection<String> targetServerNames) {
    if (ignite != null) {
      return;
    }

    if (isLocalhostOnly(targetServerNames)) {
      logger.info("spawning localhost agent");
      localhostAgent = new Agent.Node("localhost");
      localhostAgent.init();
    }

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(targetServerNames);
    spi.setIpFinder(ipFinder);

    IgniteConfiguration cfg = new IgniteConfiguration();
    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    cfg.setIgniteInstanceName("Instance@" + instanceId);

    this.ignite = Ignition.start(cfg);
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
    init(topology.getServersHostnames());
    nodesToCleanup.addAll(topology.getServersHostnames());

    Tsa tsa = new Tsa(ignite, instanceId, topology);
    controllers.add(tsa);
    return tsa;
  }

  public Tsa tsa(Topology topology, License license) {
    init(topology.getServersHostnames());
    nodesToCleanup.addAll(topology.getServersHostnames());

    Tsa tsa = new Tsa(ignite, instanceId, topology, license);
    // only keep the cluster URI if a single Tsa was created
    if (clusterURI == null) {
      clusterURI = Optional.of(tsa.clusterURI());
    } else {
      clusterURI = Optional.empty();
    }
    controllers.add(tsa);
    return tsa;
  }

  public Client client(String nodeName) {
    init(Collections.singleton(nodeName));
    nodesToCleanup.add(nodeName);

    Client client = new Client(ignite, instanceId, nodeName, clusterURI == null ? null : clusterURI.orElse(null));
    controllers.add(client);
    return client;
  }

  @Override
  public void close() throws Exception {
    for (AutoCloseable controller : controllers) {
      controller.close();
    }
    controllers.clear();

    for (String nodeName : nodesToCleanup) {
      ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
      ignite.compute(location).broadcast((IgniteRunnable) () -> Agent.CONTROLLER.cleanup(instanceId));
    }
    nodesToCleanup.clear();

    if (ignite != null) {
      ignite.close();
      ignite = null;
    }

    if (localhostAgent != null) {
      logger.info("shutting down localhost agent");
      localhostAgent.shutdown();
      localhostAgent = null;
    }

    clusterURI = null;
  }
}
