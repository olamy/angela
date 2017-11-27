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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Instance implements AutoCloseable {
  private final InstanceId instanceId;
  private final List<AutoCloseable> controllers = new ArrayList<>();
  private final Set<String> nodesToCleanup = new HashSet<>();
  private volatile Ignite ignite;

  public Instance(String idPrefix) {
    this.instanceId = new InstanceId(idPrefix);
  }

  private void init(Collection<String> targetServerNames) {
    if (ignite != null) {
      return;
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
    controllers.add(tsa);
    return tsa;
  }

  public Client client(String nodeName) {
    init(Collections.singleton(nodeName));
    nodesToCleanup.add(nodeName);

    Client client = new Client(ignite, instanceId, nodeName);
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

    if (ignite != null) {
      ignite.close();
      ignite = null;
    }
  }
}
