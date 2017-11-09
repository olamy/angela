package com.terracottatech.qa.angela;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteLock;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.terracottatech.qa.angela.kit.KitManager;
import com.terracottatech.qa.angela.tcconfig.ClusterToolConfig;
import com.terracottatech.qa.angela.topology.Topology;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;

/**
 * @author Aurelien Broszniowski
 */

public class TsaControl {

  private Topology topology;
  private ClusterToolConfig clusterToolConfig;
  private KitManager kitManager = new KitManager();

  private volatile Ignite ignite;

  public void init() {
    if (topology == null) {
      throw new IllegalArgumentException("YOu need to pass a topology");
    }

    if (ignite != null) {
      throw new IllegalStateException("You can not init TsaControl twice");
    }

    IgniteConfiguration cfg = new IgniteConfiguration();

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(Arrays.asList("tc-perf-001.eur.ad.sag"));
    spi.setIpFinder(ipFinder);

    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    cfg.setIgniteInstanceName(UUID.randomUUID().toString());
    cfg.setPeerClassLoadingEnabled(true);

    ignite = Ignition.start(cfg);

    ClusterGroup location = ignite.cluster().forAttribute("nodename", "tc-perf-001.eur.ad.sag");
    ignite.compute(location).broadcast((IgniteRunnable)() -> {

      IgniteCache<String, String> kitsInstalls = ignite.getOrCreateCache("installs");
      if (kitsInstalls.containsKey(topology.getId())) {
        System.out.println("Already exists");
      } else {
        System.out.println("Install kit");
        boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));  //TODO :get offline flag
        File kitDir = kitManager.installKit(topology.getDistributionController(), clusterToolConfig.getLicenseConfig(), offline);
//        installFiles(kitDir);

        kitsInstalls.put(topology.getId(), "install");

        System.out.println("kitDir = " + kitDir.getAbsolutePath());
//        new TerracottaInstall(kitDir, clusterConfig, managementConfig, clusterToolConfig, clusterConfig.getVersion(), agent
//            .getNetworkController())
      }
    });
  }


  public void close() {
    IgniteCache<String, String> kitsInstalls = ignite.getOrCreateCache("installs");
    kitsInstalls.remove(topology.getId());

    ignite.close();
  }

  public TsaControl withTopology(Topology topology) {
    this.topology = topology;
    return this;
  }

  public TsaControl withClusterToolConfig(final ClusterToolConfig clusterToolConfig) {
    this.clusterToolConfig = clusterToolConfig;
    return this;
  }

}
