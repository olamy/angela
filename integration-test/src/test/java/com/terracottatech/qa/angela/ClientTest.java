package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.remote.agent.SshRemoteAgentLauncher;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.client.Barrier;
import com.terracottatech.qa.angela.common.clientconfig.ClientsConfig;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.ClientTopology;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.clientconfig.ClientsConfig.*;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ClientTest {

  @Test
  public void testRemoteClient() throws Exception {
    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient")) {
      try (Client client = instance.client("localhost")) {
        Future<Void> f = client.submit((context) -> System.out.println("hello world 1"));
        f.get();
      }
      try (Client client = instance.client("localhost")) {
        Future<Void> f = client.submit((context) -> System.out.println("hello world 2"));
        f.get();
      }
    }
  }

  @Test
  public void testClientHardwareStatsLog() throws Exception {
    System.setProperty("stats", "vmstat");
    final File resultPath = new File(UUID.randomUUID().toString());

    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMixingLocalhostWithRemote", new SshRemoteAgentLauncher())) {

      ClientTopology ct = new ClientTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
          newClientsConfig().client("client1", "localhost"));

      ClientJob clientJob = (context) -> {
        System.out.println("hello");
        Thread.sleep(30000);
        System.out.println("again");
      };

      { // executeAll
        ClientArray clientArray = factory.clientArray(ct, license);

        List<Future<Void>> futures = clientArray.executeAll(clientJob);
        futures.forEach(voidFuture -> {
          try {
            voidFuture.get();
          } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
          }
        });
        Client rc = clientArray.getClients().get(0);

        rc.browse("stats").downloadTo(resultPath);
      }

    }
/*

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient")) {
      try (Client client = instance.client("localhost")) {
        Future<Void> f = client.submit((context) -> {
          System.out.println("start");
          Thread.sleep(30000);
        });
        f.get();
        client.browse("stats").downloadTo(resultPath);
      }
    }
*/

    assertThat(new File(resultPath, "vmstat.log").exists(), is(true));
    resultPath.delete();
  }


  @Test
  public void testMultipleRemoteClients() throws Exception {
    System.setProperty("tc.qa.angela.ssh.strictHostKeyChecking", "false");
    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", new SshRemoteAgentLauncher())) {
      Client client1 = instance.client(InetAddress.getLocalHost().getHostName());
      Client client2 = instance.client(InetAddress.getLocalHost().getHostName());
      Future<Void> f1 = client1.submit((context) -> System.out.println("hello world 1"));
      Future<Void> f2 = client2.submit((context) -> System.out.println("hello world 2"));
      f1.get();
      f2.get();
    } finally {
      System.clearProperty("tc.qa.angela.ssh.strictHostKeyChecking");
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

      ClientJob clientJob = (ClientJob)context -> {
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

  @Test
  public void testUploadClientJars() throws Exception {
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMixingLocalhostWithRemote", new SshRemoteAgentLauncher())) {

      final Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB);

      final ClientsConfig clientsConfig1 = newClientsConfig()
          .client("client2", "localhost")
          .client("client2-2", "localhost");

      final ClientsConfig clientsConfig2 = newClientsConfig()
          .clientSerie( 2, "tc-perf-001")
          .clientSerie( 2, "tc-perf-002");

      ClientTopology ct = new ClientTopology(distribution, clientsConfig1);

      ClientJob clientJob = (context) -> {
        System.out.println("hello");
        Thread.sleep(1000);
        System.out.println("again");
      };

      { // executeAll
        ClientArray clientArray = factory.clientArray(ct, license);

        List<Future<Void>> futures = clientArray.executeAll(clientJob);
        futures.forEach(voidFuture -> {
          try {
            voidFuture.get();
          } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
          }
        });
        Client rc = clientArray.getClients().get(0);

        rc.browse(".").downloadTo(new File("/tmp"));
      }

    }
  }

}
