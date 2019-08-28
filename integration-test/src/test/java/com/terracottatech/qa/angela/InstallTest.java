package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ClusterMonitor;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomMultiConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFile;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_A;
import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_AP;
import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_4X_A;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.SERVER_START_PREFIX;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.qa.angela.test.Versions.TERRACOTTA_VERSION;
import static com.terracottatech.qa.angela.test.Versions.TERRACOTTA_VERSION_4X;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.fail;

/**
 * @author Aurelien Broszniowski
 */
public class InstallTest {
  @Test
  public void testHardwareMetricsLogs() throws Exception {
    final File resultPath = new File("target", UUID.randomUUID().toString());

    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
            tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_AP)))
            .license(TERRACOTTA.defaultLicense()))
        .monitoring(monitoring -> monitoring.commands(EnumSet.of(HardwareMetric.DISK)));

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testHardwareStatsLogs", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0, 0);
      tsa.create(server);
      ClusterMonitor monitor = factory.monitor().startOnAll();

      Thread.sleep(3000);

      monitor.downloadTo(resultPath);
    }

    assertThat(new File(resultPath, "/localhost/disk-stats.log").exists(), is(true));
  }

  @Test
  public void testSsh() throws Exception {
    TcConfig tcConfig = tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A);
    tcConfig.updateServerHost(0, InetAddress.getLocalHost().getHostName());
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
            tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(TERRACOTTA.defaultLicense())
        );

    System.setProperty("tc.qa.angela.ssh.strictHostKeyChecking", "false");
    try (ClusterFactory factory = new ClusterFactory("InstallTest::testSsh", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();
    } finally {
      System.clearProperty("tc.qa.angela.ssh.strictHostKeyChecking");
    }
  }

  @Test
  public void testLocalInstall4x() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION_4X), KIT, LicenseType.MAX), tcConfig(version(TERRACOTTA_VERSION_4X), TC_CONFIG_4X_A)))
            .license(LicenseType.MAX.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalInstall4x", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

  @Test
  public void testLocalInstallJava9() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                    tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)
                ))
            .license(TERRACOTTA.defaultLicense())
            .terracottaCommandLineEnvironment(new TerracottaCommandLineEnvironment("1.9", null, Arrays.asList("--add-modules", "java.xml.bind", "--illegal-access=warn")))
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalInstallJava9", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

  @Test
  public void testLocalInstallWithFlightRecorder() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA), tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_AP)))
            .license(TERRACOTTA.defaultLicense())
            .terracottaCommandLineEnvironment(SERVER_START_PREFIX + "Server1", new TerracottaCommandLineEnvironment("1.8", Collections.singleton("Oracle Corporation"), Arrays.asList("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:StartFlightRecording=dumponexit=true,filename=Server1_flight_recording.jfr")))
            .terracottaCommandLineEnvironment(SERVER_START_PREFIX + "Server2", new TerracottaCommandLineEnvironment("1.8", Collections.singleton("Oracle Corporation"), Arrays.asList("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:StartFlightRecording=dumponexit=true,filename=Server2_flight_recording.jfr")))
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalInstallWithFlightRecorder", config)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll()
          .stopAll();

      TerracottaServer server = tsa.getTsaConfigurationContext().getTopology().findServer(0, 0);
      List<String> names = tsa.browse(server, ".").list().stream()
          .filter(rf -> rf.getName().endsWith("flight_recording.jfr"))
          .map(RemoteFile::getName)
          .collect(Collectors.toList());
      assertThat(names.size(), is(2));
      assertThat(names, containsInAnyOrder("Server1_flight_recording.jfr", "Server2_flight_recording.jfr"));
    }
  }

  @Test
  public void testLocalInstall() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA), tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalInstall", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

  @Ignore("The sandbox doesn't keep enough builds to make this test pass beyond a couple of days. So we keep for testing manually")
  @Test
  public void testLocalSagInstall() throws Exception {
    System.setProperty("sandbox", "TDB_PI_103oct2018");
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), PackageType.SAG_INSTALLER, TERRACOTTA),
                tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalSagInstall", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

  @Test
  public void testTwoTsaCustomConfigsFailWithoutMultiConfig() {
    Topology topology1 = new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
        tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A));
    Topology topology2 = new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
        tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_AP));

    try {
      CustomConfigurationContext.customConfigurationContext()
          .tsa(tsa -> tsa
              .topology(topology1)
              .license(TERRACOTTA.defaultLicense())
          )
          .tsa(tsa -> tsa
              .topology(topology2)
              .license(TERRACOTTA.defaultLicense())
          );
      fail("expected IllegalStateException");
    } catch (IllegalStateException ise) {
      // expected
    }
  }

  @Test
  public void testTwoTsaInstalls() throws Exception {
    Topology topology1 = new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
        tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A));
    assertThat(topology1.getServers().size(), is(1));
    Topology topology2 = new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
        tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_AP));
    assertThat(topology2.getServers().size(), is(2));

    ConfigurationContext config = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .tsa(tsa -> tsa
            .topology(topology1)
            .license(TERRACOTTA.defaultLicense())
        )
        .tsa(tsa -> tsa
            .topology(topology2)
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testTwoTsaInstalls", config)) {
      Tsa tsa1 = factory.tsa();
      tsa1.startAll();
      tsa1.licenseAll();

      Tsa tsa2 = factory.tsa();
      tsa2.startAll();
      tsa2.licenseAll();
    }
  }

  @Test
  public void testStopStalledServer() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                tcConfig(version(TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap-consistent.xml"))))
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testStopStalledServer", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0, 0);
      tsa.create(server);

      assertThat(tsa.getState(server), is(STARTING));

      tsa.stop(server);
      assertThat(tsa.getState(server), is(STOPPED));
    }
  }

  @Test
  public void testStartCreatedServer() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testStartCreatedServer", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0, 0);
      tsa.create(server);
      tsa.start(server);
      assertThat(tsa.getState(server), is(STARTED_AS_ACTIVE));
    }
  }

  @Test(expected = RuntimeException.class)
  public void testServerStartUpWithArg() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testStartCreatedServer", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0, 0);
      // Server start-up must fail due to unknown argument passed
      tsa.start(server, "--some-unknown-argument");
    }
  }


  @Test
  public void testStopPassive() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_AP)))
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testStopPassive", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();

      TerracottaServer passive = tsa.getPassive();
      System.out.println("********** stop passive");
      tsa.stop(passive);

      assertThat(tsa.getState(passive), is(TerracottaServerState.STOPPED));
      assertThat(tsa.getPassive(), is(nullValue()));

      System.out.println("********** restart passive");
      tsa.start(passive);
      assertThat(tsa.getState(passive), is(TerracottaServerState.STARTED_AS_PASSIVE));

      TerracottaServer active = tsa.getActive();
      assertThat(tsa.getState(active), is(TerracottaServerState.STARTED_AS_ACTIVE));
      System.out.println("********** stop active");
      tsa.stop(active);
      assertThat(tsa.getState(active), is(TerracottaServerState.STOPPED));

      System.out.println("********** wait for passive to become active");
      await().atMost(15, SECONDS).until(() -> tsa.getState(passive), is(TerracottaServerState.STARTED_AS_ACTIVE));

      System.out.println("********** done, shutting down");
    }
  }
}
