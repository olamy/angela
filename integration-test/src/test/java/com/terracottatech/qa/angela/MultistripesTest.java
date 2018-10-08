package com.terracottatech.qa.angela;

import com.terracotta.connection.api.DetailedConnectionException;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.net.ClientToServerDisruptor;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
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

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.fail;

/**
 * @author Aurelien Broszniowski
 */

public class MultistripesTest {

  @Test
  public void test2StripesSsh() throws Exception {
    InetAddress local = InetAddress.getLocalHost();
    TcConfig tcConfig1 = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml"));
    tcConfig1.updateServerHost(0, local.getHostName());
    tcConfig1.updateServerHost(1, local.getHostName());
    TcConfig tcConfig2 = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml"));
    tcConfig2.updateServerHost(0, local.getHostAddress());
    tcConfig2.updateServerHost(1, local.getHostAddress());

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), tcConfig1, tcConfig2))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );

    System.setProperty("tc.qa.angela.ssh.strictHostKeyChecking", "false");
    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2StripesSsh", configContext)) {
      factory.tsa()
          .startAll()
          .licenseAll();
    } finally {
      System.clearProperty("tc.qa.angela.ssh.strictHostKeyChecking");
    }
  }

  @Test
  public void testUniquenessOfSymbolicNamesInConfigs() {
    TcConfig tcConfig1 = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml"));
    TcConfig tcConfig2 = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml"));

    try {
      new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), tcConfig1, tcConfig2);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  @Test
  public void test2Stripes() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml")),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes", configContext)) {
      factory.tsa()
          .startAll()
          .licenseAll();
    }
  }

  @Test
  public void testUpgrade() throws Exception {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml")),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml")));
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> {
              tsa.topology(topology)
          .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")));
            }
        );

//    System.setProperty("tc.qa.angela.skipUninstall", "true");

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      TerracottaServer server = topology.findServer(0, 0);

      tsa.stop(server);
      tsa.browse(server, "dataroot/Server1-1").downloadTo(new File("target/dataroot"));
      tsa.upgrade(server, distribution(version("10.3.0.1.80"), PackageType.KIT, LicenseType.TC_DB));
      tsa.browse(server, ".").upload("dataroot/Server1-1", new File("target/dataroot"));
      tsa.start(server);

      tsa.stopAll();
    }
  }

  @Test
  public void test2StripesDisrupt() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), true,
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml")),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
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
        assertThat(se.getCause(), instanceOf(DetailedConnectionException.class));
        assertThat(se.getCause().getCause(), instanceOf(TimeoutException.class));
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
