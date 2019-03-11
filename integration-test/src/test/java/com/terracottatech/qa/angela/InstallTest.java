package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ClusterMonitor;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomMultiConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFile;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.metrics.HardwareMetric;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
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

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTING;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STOPPED;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
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
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
            tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"))))
        .monitoring(monitoring -> monitoring.commands(EnumSet.of(HardwareMetric.DISK)));

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testHardwareStatsLogs", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0,0);
      tsa.create(server);
      ClusterMonitor monitor = factory.monitor().startOnAll();

      Thread.sleep(3000);

      monitor.downloadTo(resultPath);
    }

    assertThat(new File(resultPath, "/localhost/disk-stats.log").exists(), is(true));
  }

  @Test
  public void testSsh() throws Exception {
    TcConfig tcConfig = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"));
    tcConfig.updateServerHost(0, InetAddress.getLocalHost().getHostName());
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
            tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
                tcConfig(version(Versions.TERRACOTTA_VERSION_4X), getClass().getResource("/terracotta/4/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/4/terracotta-license.key")))
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
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml")))
            )
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap.xml")))
            )
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .terracottaCommandLineEnvironment(TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.SERVER_START_PREFIX + "Server1", new TerracottaCommandLineEnvironment("1.8", Collections.singleton("Oracle Corporation"), Arrays.asList("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:StartFlightRecording=dumponexit=true,filename=Server1_flight_recording.jfr")))
            .terracottaCommandLineEnvironment(TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.SERVER_START_PREFIX + "Server2", new TerracottaCommandLineEnvironment("1.8", Collections.singleton("Oracle Corporation"), Arrays.asList("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:StartFlightRecording=dumponexit=true,filename=Server2_flight_recording.jfr")))
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalInstallWithFlightRecorder", config)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll()
          .stopAll();

      TerracottaServer server = tsa.getTsaConfigurationContext().getTopology().findServer(0, 0);
      List<String> names = tsa.browse(server, ".").list().stream().filter(rf -> rf.getName().endsWith("flight_recording.jfr")).map(RemoteFile::getName).collect(Collectors.toList());
      assertThat(names.size(), is(2));
      assertThat(names, containsInAnyOrder("Server1_flight_recording.jfr", "Server2_flight_recording.jfr"));
    }
  }

  @Test
  public void testLocalInstall() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.SAG_INSTALLER, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testLocalSagInstall", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

  @Test
  public void testTwoTsaCustomConfigsFailWithoutMultiConfig() throws Exception {
    Topology topology1 = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    Topology topology2 = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap.xml")));

    try {
      CustomConfigurationContext.customConfigurationContext()
          .tsa(tsa -> tsa
              .topology(topology1)
              .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
          )
          .tsa(tsa -> tsa
              .topology(topology2)
              .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
          );
      fail("expected IllegalStateException");
    } catch (IllegalStateException ise) {
      // expected
    }
  }

  @Test
  public void testTwoTsaInstalls() throws Exception {
    Topology topology1 = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    assertThat(topology1.getServers().size(), is(1));
    Topology topology2 = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap.xml")));
    assertThat(topology2.getServers().size(), is(2));

    ConfigurationContext config = CustomMultiConfigurationContext.customMultiConfigurationContext()
        .tsa(tsa -> tsa
            .topology(topology1)
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        )
        .tsa(tsa -> tsa
            .topology(topology2)
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap-consistent.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );


    try (ClusterFactory factory = new ClusterFactory("InstallTest::testStartCreatedServer", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0,0);
      tsa.create(server);
      tsa.start(server);
      assertThat(tsa.getState(server), is(STARTED_AS_ACTIVE));
    }
  }

  @Test (expected = RuntimeException.class)
  public void testServerStartUpWithArg() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
                                                            .tsa(tsa -> tsa
                                                                .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                                                                                       tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
                                                                .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
                                                            );


    try (ClusterFactory factory = new ClusterFactory("InstallTest::testStartCreatedServer", config)) {
      Tsa tsa = factory.tsa();

      TerracottaServer server = config.tsa().getTopology().findServer(0,0);
      // Server start-up must fail due to unknown argument passed
      tsa.start(server, "--some-unknown-argument");
    }
  }


  @Test
  public void testStopPassive() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
