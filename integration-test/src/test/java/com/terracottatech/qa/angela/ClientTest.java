package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.client.Barrier;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.junit.Assert.fail;

public class ClientTest {

  @Test
  public void testRemoteClient() throws Exception {
    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient")) {
      try (Client client = instance.client("localhost")) {
        Future<Void> f = client.submit((context) -> System.out.println("hello world"));
        f.get();
      }
    }
  }

  @Test
  public void testRemoteClientWithJdk9() throws Exception {
    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient")) {
      try (Client client = instance.client("localhost", new TerracottaCommandLineEnvironment("1.9", null, Arrays.asList("--illegal-access=warn", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")))) {
        Future<Void> f = client.submit((context) -> System.out.println("hello JDK 9 world"));
        f.get();
      }
    }
  }

  @Test
  public void testMixingLocalhostWithRemote() throws Exception {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMixingLocalhostWithRemote")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();

      try {
        factory.client("remote-server");
        fail("expected exception");
      } catch (Exception e) {
        // expected
      }
    }
  }

  @Test
  public void testBarrier() throws Exception {
    final int clientCount = 2;
    try (ClusterFactory factory = new ClusterFactory("ClientTest::testBarrier")) {

      ClientJob clientJob = (ClientJob) context -> {
        Barrier daBarrier = context.barrier("daBarrier", clientCount);
        for (int i = 0; i < 20; i++) {
          daBarrier.await();
        }
      };

      List<Future<Void>> futures = new ArrayList<>();
      for (int i = 0; i < clientCount; i++) {
        Client client = factory.client("localhost");
        Future<Void> future = client.submit(clientJob);

        futures.add(future);
      }

      // if the barrier hangs forever, one of those futures will timeout on get and throw
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    }
  }

}
