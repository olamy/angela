package com.terracottatech.qa.angela;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.util.Arrays;
import java.util.UUID;

/**
 * @author Aurelien Broszniowski
 */

public class TsaControl {


  private volatile Ignite ignite;
  private String id;

  public void init(final String id) {
    if (ignite!=null) {
      throw new IllegalStateException("You can not init TsaControl twice");
    }
    this.id = id;

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
      if (kitsInstalls.containsKey(id)) {
        System.out.println("Already exists");
      } else {
        System.out.println("Install kit");
        kitsInstalls.put(id, "Installed");
      }
    });
  }


  public void close() {
    IgniteCache<String, String> kitsInstalls = ignite.getOrCreateCache("installs");
    kitsInstalls.remove(this.id);

    ignite.close();
  }
}
