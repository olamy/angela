package com.terracottatech.qa.angela;

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
import com.terracottatech.qa.angela.common.clientconfig.ClientId;
import com.terracottatech.qa.angela.common.cluster.AtomicCounter;
import com.terracottatech.qa.angela.common.cluster.AtomicReference;
import com.terracottatech.qa.angela.common.cluster.Barrier;
import com.terracottatech.qa.angela.common.cluster.Cluster;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.metrics.MonitoringCommand;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.test.Versions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.tc.util.Assert.assertNotNull;
import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_A;
import static com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment.DEFAULT;
import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class ClientTest {
  @Test
  public void testStopAll() throws Exception {
    String gcLogFileName = "gclog.log";
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> {
          List<String> javaOpts = Arrays.asList("-XX:+PrintGCDetails", "-XX:+PrintGCApplicationStoppedTime",
              "-XX:+PrintGCApplicationConcurrentTime", "-XX:+PrintGCDateStamps", "-Xloggc:" + gcLogFileName);
          clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(2, "localhost")))
              .terracottaCommandLineEnvironment(DEFAULT.withJavaVendor("Oracle Corporation").withJavaOpts(javaOpts));
        });

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      ClientArray clientArray = instance.clientArray();
      clientArray.executeOnAll((cluster) -> { }).get();
      clientArray.stopAll();

      for (Client client : clientArray.getClients()) {
        Path targetFolder = Paths.get("target", "testStopAll", client.getSymbolicName());
        Files.createDirectories(targetFolder);
        Path targetFile = targetFolder.resolve(gcLogFileName);
        clientArray.browse(client, ".").list().stream().filter(rf -> rf.getName().startsWith(gcLogFileName)).forEach(rf -> {
          try {
            assertThat(rf.toTransportableFile().getContent().length, greaterThan(0));
            rf.downloadTo(targetFile.toFile());
          } catch (IOException ioe) {
            throw new RuntimeException(ioe);
          }
        });
        assertThat(Files.isRegularFile(targetFile), is(true));
        assertThat(Files.size(targetFile), greaterThan(0L));
      }
    }
  }

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
        Path downloadPath = Paths.get(localFolder, clientHostname, downloadedFile);
        String downloadedFileContent = new String(Files.readAllBytes(downloadPath));
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

        for (Client client: clientArray.getClients()) {
          Path downloadPath = Paths.get(localFolder, client.getSymbolicName(), downloadedFile);
          String downloadedFileContent = new String(Files.readAllBytes(downloadPath));
          filecontents.remove(downloadedFileContent);
        }
        assertThat(filecontents.size(), is(equalTo(0)));
      }
    }
  }

  @Test
  public void testMultipleClientJobsSameHostDownloadFiles() throws Exception {
    String clientHostname = "localhost";
    int clientsCount = 3;
    int clientsPerMachine = 2;
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(clientsCount, clientHostname))));

    String remoteFolder = "testFolder";
    String downloadedFile = "myNewFile.txt";
    String fileContent = "Test data";
    String localFolder = "target/myNewFolderMultipleJobs";

    try (ClusterFactory instance = new ClusterFactory("ClientTest::testRemoteClient", configContext)) {
      try (ClientArray clientArray = instance.clientArray()) {
        Set<String> filecontents = new HashSet<>();

        ClientJob clientJob = (cluster) -> {
          String clientFileContent = fileContent + UUID.randomUUID();
          filecontents.add(clientFileContent);
          System.out.println("Writing to file : " + clientFileContent);
          String symbolicName = cluster.getClientId()
                  .getSymbolicName()
                  .getSymbolicName();
          Long jobNumber = cluster.atomicCounter(symbolicName, 0)
                  .incrementAndGet();
          File jobFolder = new File(remoteFolder, jobNumber.toString());
          jobFolder.mkdirs();
          Path path = Paths.get(jobFolder.getPath(), downloadedFile);
          System.out.println("REMOTE PATH: " + path);
          Files.write(path, clientFileContent.getBytes());
          System.out.println("Done");
        };
        ClientArrayFuture f = clientArray.executeOnAll(clientJob, clientsPerMachine);
        f.get();
        clientArray.download(remoteFolder, new File(localFolder));

        for (Client client: clientArray.getClients()) {
          String symbolicName = client.getSymbolicName();
          for (Integer jobNumber = 1; jobNumber <= 2; jobNumber++) {
            Path downloadPath = Paths.get(localFolder, symbolicName, jobNumber.toString(), downloadedFile);
            String downloadedFileContent = new String(Files.readAllBytes(downloadPath));
            filecontents.remove(downloadedFileContent);
          }
        }
        assertThat(filecontents.size(), is(equalTo(0)));
      }
    }
  }

  @Test
  public void testMultipleClientsOnSameHost() throws Exception {
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA);
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray
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
  public void testMultipleClientJobsOnSameMachine() throws Exception {
    int serieLength = 3;
    int clientsPerMachine = 2;
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA);
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray
            .clientArrayTopology(
                new ClientArrayTopology(
                    distribution,
                    newClientArrayConfig().hostSerie(serieLength, "localhost")
                )
            )
        );
    try (ClusterFactory factory = new ClusterFactory("ClientTest::testMultipleClientsOnSameHost", configContext)) {
      try (ClientArray clientArray = factory.clientArray()) {
        ClientJob clientJob = (cluster) -> {
          AtomicCounter counter = cluster.atomicCounter("countJobs", 0);
          long n = counter.incrementAndGet();
          System.out.println("hello world from job number " + n);
        };
        ClientArrayFuture f = clientArray.executeOnAll(clientJob, clientsPerMachine);
        f.get();

        long expectedJobs = clientsPerMachine * serieLength;
        long actualJobs = factory.cluster()
                .atomicCounter("countJobs", 0)
                .get();
        assertThat(actualJobs, is(expectedJobs));
      }
    }
  }

  @Test
  public void testRemoteClient() throws Exception {
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA);
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig().host("localhost"))))
        .clientArray(clientArray -> clientArray
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
              .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig()
                  .host("localhost")))
          );
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testClientCpuMetricsLogs() throws Exception {
    final Path resultPath = Paths.get("target", UUID.randomUUID().toString());

    final String clientHostname = "localhost";
    ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
        newClientArrayConfig().host(clientHostname));

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense()).clientArrayTopology(ct))
        .monitoring(monitoring -> monitoring.commands(EnumSet.of(HardwareMetric.CPU)));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testClientCpuMetricsLogs", configContext)) {
      ClusterMonitor monitor = factory.monitor();
      ClientJob clientJob = (cluster) -> {
        System.out.println("hello");
        Thread.sleep(18000);
        System.out.println("again");
      };

      ClientArray clientArray = factory.clientArray();
      monitor.startOnAll();

      ClientArrayFuture future = clientArray.executeOnAll(clientJob);
      future.get();

      monitor.downloadTo(resultPath.toFile());
      monitor.stopOnAll();

      monitor.processMetrics((hostname, transportableFile) -> {
        assertThat(hostname, is(clientHostname));
        assertThat(transportableFile.getName(), is("cpu-stats.log"));
        byte[] content = transportableFile.getContent();
        assertNotNull(content);
        assertThat(content.length, greaterThan(0));
      });
    }
  }

  @Test
  public void testClientAllHardwareMetricsLogs() throws Exception {
    final Path resultPath = Paths.get("target", UUID.randomUUID().toString());

    final String clientHostname = "localhost";
    ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
        newClientArrayConfig().host(clientHostname));

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense()).clientArrayTopology(ct))
        .monitoring(monitoring -> monitoring.commands(EnumSet.allOf(HardwareMetric.class)));

    try (ClusterFactory factory = new ClusterFactory("ClientTest::testClientAllHardwareMetricsLogs", configContext)) {
      ClusterMonitor monitor = factory.monitor();
      ClientJob clientJob = (cluster) -> {
        System.out.println("hello");
        Thread.sleep(18000);
        System.out.println("again");
      };

      ClientArray clientArray = factory.clientArray();
      monitor.startOnAll();

      ClientArrayFuture future = clientArray.executeOnAll(clientJob);
      future.get();

      monitor.downloadTo(resultPath.toFile());
      monitor.stopOnAll();
    }

    final Path statFile = resultPath.resolve(clientHostname);
    assertMetricsFile(statFile.resolve("cpu-stats.log"));
    assertMetricsFile(statFile.resolve("disk-stats.log"));
    assertMetricsFile(statFile.resolve("memory-stats.log"));
    assertMetricsFile(statFile.resolve("network-stats.log"));
  }

  @Test
   public void testClientDummyMemoryMetrics() throws Exception {
     final Path resultPath = Paths.get("target", UUID.randomUUID().toString());

     final String clientHostname = "localhost";
     ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
         newClientArrayConfig().host(clientHostname));

     HardwareMetric metric = HardwareMetric.MEMORY;
     ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
         .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense()).clientArrayTopology(ct))
         .monitoring(monitoring -> monitoring.command(metric, new MonitoringCommand("dummy", "command")));

     try (ClusterFactory factory = new ClusterFactory("ClientTest::testClientDummyMemoryMetrics", configContext)) {
       ClusterMonitor monitor = factory.monitor();
       ClientJob clientJob = (cluster) -> {
         System.out.println("hello");
         Thread.sleep(18000);
         System.out.println("again");
       };

       ClientArray clientArray = factory.clientArray();
       monitor.startOnAll();

       ClientArrayFuture future = clientArray.executeOnAll(clientJob);
       future.get();

       monitor.downloadTo(resultPath.toFile());
       assertThat(monitor.isMonitoringRunning(metric), is(false));

       monitor.stopOnAll();
     }
   }

  @Test(expected = IllegalStateException.class)
   public void testClusterMonitorWhenNoMonitoringSpecified() throws Exception {
     ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
         newClientArrayConfig().host("localhost"));

     ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
         .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense()).clientArrayTopology(ct));

     try (ClusterFactory factory = new ClusterFactory("ClientTest::testClientDummyMemoryMetrics", configContext)) {
       factory.monitor();
     }
   }

  @Test
  public void testMultipleRemoteClients() throws Exception {
    // Don't run on Windows as Jenkins Windows machines don't have SSH server installed
    assumeFalse(OS.INSTANCE.isWindows());

    System.setProperty("tc.qa.angela.ssh.strictHostKeyChecking", "false");
    ClientArrayTopology ct = new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
        newClientArrayConfig().hostSerie(2, InetAddress.getLocalHost().getHostName()));
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense()).clientArrayTopology(ct));
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
        .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense())
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig()
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
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
            tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        )
        .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense())
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig()
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
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA);
    ConfigurationContext configContext = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .clientArray(clientArray -> clientArray
            .clientArrayTopology(new ClientArrayTopology(distribution, newClientArrayConfig().host("localhost"))))
        .clientArray(clientArray -> clientArray
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
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA);
    ClientArrayConfig clientArrayConfig1 = newClientArrayConfig()
        .host("client2", "localhost")
        .host("client2-2", "localhost");

    ClientArrayTopology ct = new ClientArrayTopology(distribution, clientArrayConfig1);

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense()).clientArrayTopology(ct));

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

  @Test
  public void testClientArrayHostNames() throws Exception {
    ClientArrayConfig hostSerie = newClientArrayConfig()
            .hostSerie(2, "localhost");
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(hostSerie)));
    try (ClusterFactory factory = new ClusterFactory("ClientTest::testClientArrayReferenceShared", configContext)) {
      try (ClientArray clientArray = factory.clientArray()) {
        ClientJob clientJob = (Cluster cluster) -> {
          ClientId clientId = cluster.getClientId();
          assertThat(clientId.getHostname(), is("localhost"));
          assertThat(clientId.getSymbolicName().getSymbolicName(),
                  anyOf(is("localhost-0"), is("localhost-1")));
        };
        ClientArrayFuture f = clientArray.executeOnAll(clientJob);
        f.get();
        Cluster cluster = factory.cluster();
        assertNull(cluster.getClientId());
      }
    }
  }

  private void assertMetricsFile(Path path) throws IOException {
    assertThat(Files.exists(path), is(true));
    assertThat(Files.readAllLines(path).size(), is(greaterThan(0)));
  }
}
