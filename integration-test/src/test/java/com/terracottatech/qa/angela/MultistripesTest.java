package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.net.ClientToServerDisruptor;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.test.Versions;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetReader;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.Record;
import com.terracottatech.store.StoreException;
import com.terracottatech.store.Type;
import com.terracottatech.store.configuration.DatasetConfigurationBuilder;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import org.junit.Test;
import org.terracotta.connection.ConnectionException;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_MULTISTRIPE1;
import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_MULTISTRIPE2;
import static com.terracottatech.qa.angela.common.AngelaProperties.SSH_STRICT_HOST_CHECKING;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

/**
 * @author Aurelien Broszniowski
 */

public class MultistripesTest {
  @Test
  public void test2StripesSsh() throws Exception {
    // Don't run on Windows as Jenkins Windows machines don't have SSH server installed
    assumeFalse(OS.INSTANCE.isWindows());

    InetAddress local = InetAddress.getLocalHost();
    TcConfig tcConfig1 = tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE1);
    tcConfig1.updateServerHost(0, local.getHostName());
    tcConfig1.updateServerHost(1, local.getHostName());
    TcConfig tcConfig2 = tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE2);
    tcConfig2.updateServerHost(0, local.getHostAddress());
    tcConfig2.updateServerHost(1, local.getHostAddress());

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), tcConfig1, tcConfig2))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        );

    SSH_STRICT_HOST_CHECKING.setProperty("false");
    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2StripesSsh", configContext)) {
      factory.tsa()
          .startAll()
          .licenseAll();
    } finally {
      SSH_STRICT_HOST_CHECKING.clearProperty();
    }
  }

  @Test
  public void testUniquenessOfSymbolicNamesInConfigs() {
    TcConfig tcConfig1 = tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE1);
    TcConfig tcConfig2 = tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE1);

    try {
      new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), tcConfig1, tcConfig2);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  @Test
  public void test2Stripes() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
            tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE1),
            tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE2)))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes", configContext)) {
      factory.tsa()
          .startAll()
          .licenseAll();
    }
  }

  @Test
  public void testUpgrade() throws Exception {
    String baseVersion = "10.3.1.0.102";
    String newVersion = "10.3.1.1.12";
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(baseVersion), PackageType.KIT, LicenseType.TERRACOTTA),
                tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE1),
                tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE2)))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      TerracottaServer server = configContext.tsa().getTopology().getServer(0, 0);

      tsa.stop(server);
      tsa.browse(server, "dataroot/Server1-1").downloadTo(new File("target/dataroot"));
      tsa.upgrade(server, distribution(version(newVersion), PackageType.KIT, LicenseType.TERRACOTTA));
      tsa.browse(server, "dataroot/Server1-1").upload(new File("target/dataroot"));
      tsa.start(server);

      tsa.stopAll();
    }
  }

  @Test
  public void test2StripesDisrupt() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), true,
            tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE1),
            tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_MULTISTRIPE2)))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      ClientToServerDisruptor clientToServerDisruptor = tsa.disruptionController().newClientToServerDisruptor();
      clientToServerDisruptor.disrupt();

      URI uri = tsa.uri();
      System.err.println("Connecting to : " + uri);
      try {
        DatasetManager.clustered(uri).build();
        fail("expected StoreException");
      } catch (StoreException se) {
        // expected, the client shouldn't be able to connect as client-to-server is disrupted
        assertThat(se.getCause(), instanceOf(ConnectionException.class));
      }

      // undisrupt now & try reconnecting
      clientToServerDisruptor.undisrupt();

      try (DatasetManager datasetManager = DatasetManager.clustered(uri).build()) {
        DatasetConfigurationBuilder builder = datasetManager.datasetConfiguration()
            .offheap("primary-server-resource");

        datasetManager.newDataset("MyDataset", Type.STRING, builder.build());
        Dataset<String> dataset = datasetManager.getDataset("MyDataset", Type.STRING);

        DatasetWriterReader<String> writerReader = dataset.writerReader();
        writerReader.add("ONE", CellDefinition.defineLong("val").newCell(1L));

        DatasetReader<String> reader = dataset.reader();
        Optional<Record<String>> one = reader.get("ONE");
        assertThat(one.isPresent(), is(true));
      }
    }
  }
}
