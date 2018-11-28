package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.common.cluster.AtomicReference;
import com.terracottatech.qa.angela.common.cluster.Cluster;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ClientTest {

  @Test
  public void testClientArrayDownloadFiles() throws Exception {
    final String clientHostname = "localhost";
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host(clientHostname))));

    String remoteFolder = "testFolder";
    String downloadedFile = "myNewFile.txt";
    String fileContent = "Test data";
    String localFolder = "target/myNewFolderClient";

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> {
          System.out.println("Writing to file");
          new File(remoteFolder).mkdirs();
          Files.write(Paths.get(remoteFolder, downloadedFile), fileContent.getBytes());
          System.out.println("Done");
        });
        f.get();
        clientArray.download(remoteFolder, new File(localFolder));
        String downloadedFileContent = new String(Files.readAllBytes(Paths.get(localFolder, clientHostname + "-1", downloadedFile)));
        assertThat(downloadedFileContent, is(equalTo(fileContent)));
      }
    }
  }

  @Test
  public void testMultipleClientsSameHostArrayDownloadFiles() throws Exception {
    String clientHostname = "localhost";
    int clientsCount = 3;
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(clientsCount, clientHostname))));

    String remoteFolder = "testFolder";
    String downloadedFile = "myNewFile.txt";
    String fileContent = "Test data";
    String localFolder = "target/myNewFolderMultipleClients";

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        Set<String> filecontents = new HashSet<>();

        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> {
          String clientFileContent = fileContent + UUID.randomUUID();
          filecontents.add(clientFileContent);
          System.out.println("Writing to file : " + clientFileContent);
          new File(remoteFolder).mkdirs();
          Files.write(Paths.get(remoteFolder, downloadedFile), clientFileContent.getBytes());
          System.out.println("Done");
        });
        f.get();
        clientArray.download(remoteFolder, new File(localFolder));

        for (int i = 1; i < clientsCount + 1; i++) {
          String downloadedFileContent = new String(Files.readAllBytes(Paths.get(localFolder, clientHostname + "-" + i, downloadedFile)));
          filecontents.remove(downloadedFileContent);
        }
        assertThat(filecontents.size(), is(equalTo(0)));
      }
    }
  }

  @Test
  public void testMultipleClientsOnSameHost() throws Exception {
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB);
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray.license(license)
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig()
                .hostSerie(3, "localhost")
            )));

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testMultipleClientsOnSameHost", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> System.out.println("hello world"));
        f.get();
      }
    }
  }

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
  public void testClientArrayNoDistribution() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))));

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> System.out.println("hello world 1"));
        f.get();
      }
    }
  }

  @Test
  public void testClientArrayExceptionReported() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(2, "localhost"))));

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testClientArrayExceptionReported", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> {
          String message = "Just Say No (tm) " + cluster.atomicCounter("testClientArrayExceptionReportedCounter", 0L)
              .getAndIncrement();
          throw new RuntimeException(message);
        });
        try {
          f.get();
          fail("expected ExecutionException");
        } catch (ExecutionException ee) {
          assertThat(exceptionToString(ee), containsString("Just Say No (tm) 0"));
          assertThat(exceptionToString(ee), containsString("Just Say No (tm) 1"));
        }
      }
    }
  }

  private static String exceptionToString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.close();
    return sw.toString();
  }

  @Test
  public void testClientArrayMissingLicenseCheck() {
    try {
      CustomConfigurationContext.customConfigurationContext()
          .clientArray(clientArray -> clientArray
              .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), newClientArrayConfig()
                  .host("localhost")))
          );
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testClientHardwareMetricsLog() throws Exception {
    final File resultPath = new File("target", UUID.randomUUID().toString());

    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    final String clientHostname = "localhost";
    ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        newClientArrayConfig().host(clientHostname));

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

    assertThat(new File(resultPath, clientHostname + "/metrics/vmstat.log").exists(), is(true));
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

  @Test
  public void testClientArrayReferenceShared() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(2, "localhost"))));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testClientArrayReferenceShared", configContext)) {
      try (ClientArray clientArray = factory.clientArray()) {
        clientArray.getClients().size();
        ClientArrayFuture f = clientArray.executeOnAll((cluster) -> {
          AtomicReference<String> strRef = cluster.atomicReference("string", null);
          strRef.set("A");

          AtomicReference<Integer> intRef = cluster.atomicReference("int", 0);
          intRef.compareAndSet(0, 1);
        });
        f.get();
        Cluster cluster = factory.cluster();

        AtomicReference<String> strRef = cluster.atomicReference("string", "X");
        assertThat(strRef.get(), is("A"));

        AtomicReference<Integer> intRef = cluster.atomicReference("int", 0);
        assertThat(intRef.get(), is(1));
      }
    }
  }
}
