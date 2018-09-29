package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClientArrayFuture;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ClusterMonitor;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomMultiConfigurationContext;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig;
import com.terracottatech.qa.angela.common.cluster.AtomicCounter;
import com.terracottatech.qa.angela.common.cluster.Barrier;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ClientTest {

  @Test
  public void testRemoteClient() throws Exception {
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB);
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray.license(license)
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig().host("localhost"))))
        .clientArray(clientArray -> clientArray.license(license)
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig().host("localhost"))));

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> System.out.println("hello world 1"));
        f.get();
      }
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> System.out.println("hello world 2"));
        f.get();
      }
    }
  }

  @Test
  public void testClientHardwareStatsLog() throws Exception {
    final File resultPath = new File(UUID.randomUUID().toString());

    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        newClientArrayConfig().host("localhost"));

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(license).clientArrayTopology(ct));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMixingLocalhostWithRemote", configContext)) {

      ClusterMonitor monitor = factory.monitor();

      ClientJob clientJob = (cluster) -> {
        System.out.println("hello");
        Thread.sleep(3000);
        System.out.println("again");
      };

      { // executeAll
        ClientArray clientArray = factory.clientArray();
        monitor.startOnAll();

        ClientArrayFuture future = clientArray.executeOnAll(clientJob);
        future.get();
        Client rc = clientArray.getClients().stream().findFirst().get();

        monitor.downloadTo(resultPath);
        monitor.stopOnAll();
      }

    }

    assertThat(new File(resultPath, "/localhost/stats/vmstat.log").exists(), is(true));
    resultPath.delete();
  }


  @Test
  public void testMultipleRemoteClients() throws Exception {
    System.setProperty("tc.qa.angela.ssh.strictHostKeyChecking", "false");
    ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        newClientArrayConfig().hostSerie(2, InetAddress.getLocalHost().getHostName()));
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .clientArrayTopology(ct));
    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      ClientArray clientArray = instance.clientArray();
      ClientArrayFuture f1 = clientArray.executeOnAll((cluster) -> System.out.println("hello world 1"));
      ClientArrayFuture f2 = clientArray.executeOnAll((cluster) -> System.out.println("hello world 2"));
      f1.get();
      f2.get();
    } finally {
      System.clearProperty("tc.qa.angela.ssh.strictHostKeyChecking");
    }
  }

  @Test
  public void testRemoteClientWithJdk9() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), newClientArrayConfig()
                .host("localhost")))
            .terracottaCommandLineEnvironment(new TerracottaCommandLineEnvironment("1.9", null, Arrays.asList("--illegal-access=warn", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")))
        );

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> System.out.println("hello JDK 9 world"));
        f.get();
      }
    }
  }

  @Test
  public void testMixingLocalhostWithRemote() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
            tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        )
        .clientArray(clientArray -> clientArray.license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), newClientArrayConfig()
                .host("remote-server")))
        );

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMixingLocalhostWithRemote", configContext)) {
      factory.tsa();

      try {
        factory.clientArray();
        fail("expected exception");
      } catch (Exception e) {
        // expected
      }
    }
  }

  @Test
  public void testBarrier() throws Exception {
    final int clientCount = 2;
    final int loopCount = 20;
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB);
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray.license(license)
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig().host("localhost"))))
        .clientArray(clientArray -> clientArray.license(license)
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig().host("localhost"))));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testBarrier", configContext)) {

      ClientJob clientJob = cluster -> {
        Barrier daBarrier = cluster.barrier("daBarrier", clientCount);
        for (int i = 0; i < loopCount; i++) {
          daBarrier.await();
          AtomicCounter counter = cluster.atomicCounter("ClientTest::testBarrier::counter", 0L);
          counter.incrementAndGet();
        }
      };

      List<Future<Void>> futures = new ArrayList<>();
      for (int i = 0; i < clientCount; i++) {
        ClientArray clients = factory.clientArray();
        ClientArrayFuture caf = clients.executeOnAll(clientJob);
        futures.addAll(caf.getFutures());
      }

      // if the barrier hangs forever, one of those futures will timeout on get and throw
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }

      AtomicCounter counter = factory.cluster().atomicCounter("ClientTest::testBarrier::counter", 0L);
      assertThat(counter.get(), is((long)clientCount * loopCount));
    }
  }

  @Test
  public void testUploadClientJars() throws Exception {
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB);
    ClientArrayConfig clientArrayConfig1 = newClientArrayConfig()
        .host("client2", "localhost")
        .host("client2-2", "localhost");

    ClientArrayTopology ct = new ClientArrayTopology(distribution, clientArrayConfig1);

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.license(license))
        .clientArray(clientArray -> clientArray.license(license).clientArrayTopology(ct));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMixingLocalhostWithRemote", configContext)) {
      ClientJob clientJob = (cluster) -> {
        System.out.println("hello");
        Thread.sleep(1000);
        System.out.println("again");
      };

      { // executeAll
        ClientArray clientArray = factory.clientArray();

        ClientArrayFuture f = clientArray.executeOnAll(clientJob);
        f.get();
        Client rc = clientArray.getClients().stream().findFirst().get();

        rc.browse(".").downloadTo(new File("/tmp"));
      }

    }
  }

}
