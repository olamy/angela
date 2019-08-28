package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClientArrayFuture;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tms;
import com.terracottatech.qa.angela.client.TmsHttpClient;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.Type;
import com.terracottatech.store.configuration.DatasetConfigurationBuilder;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_A;
import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Anthony Dahanne
 */

public class TmsTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(TmsTest.class);

  private static String connectionName;
  private static ClusterFactory factory;
  private static final String TMS_HOSTNAME = "localhost";
  private static URI tsaUri;
  private static TmsHttpClient tmsHttpClient;

  @BeforeClass
  public static void setUp() {
    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA);
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution,
            tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_A)))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        ).tms(tms -> tms.distribution(distribution)
            .license(LicenseType.TERRACOTTA.defaultLicense())
            .hostname(TMS_HOSTNAME)
        ).clientArray(clientArray -> clientArray.license(LicenseType.TERRACOTTA.defaultLicense())
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig().host("localhost")))
        );

    factory = new ClusterFactory("TmsTest::testConnection", configContext);
    Tsa tsa = factory.tsa()
        .startAll()
        .licenseAll();
    tsaUri = tsa.uri();
    Tms tms = factory.tms()
        .start();
    tmsHttpClient = tms.httpClient();
    connectionName = tmsHttpClient.createConnectionToCluster(tsaUri);
  }

  @Test
  public void testConnectionName() throws Exception {
    assertThat(connectionName, startsWith("TmsTest"));
  }

  @Test
  public void testTmsConnection() throws Exception {
    URI tsaUri = TmsTest.tsaUri;
    TmsHttpClient tmsHttpClient = TmsTest.tmsHttpClient;

    ClientArray clientArray = factory.clientArray();
    ClientJob clientJob = (cluster) -> {
      try (DatasetManager datasetManager = DatasetManager.clustered(tsaUri).build()) {
        DatasetConfigurationBuilder builder = datasetManager.datasetConfiguration()
            .offheap("primary-server-resource");
        boolean datasetCreated = datasetManager.newDataset("MyDataset", Type.STRING, builder.build());
        if (datasetCreated) {
          LOGGER.info("created dataset");
        }
        try (Dataset<String> dataset = datasetManager.getDataset("MyDataset", Type.STRING)) {
          DatasetWriterReader<String> writerReader = dataset.writerReader();
          writerReader.add("ONE", CellDefinition.defineLong("val").newCell(1L));
          LOGGER.info("Value created for key ONE");
        }

      }
      LOGGER.info("client done");
    };


    ClientJob clientJobTms = (cluster) -> {
      String response = tmsHttpClient.sendGetRequest("/api/connections");
      LOGGER.info("tms list connections result : " + response);
      assertThat(response, Matchers.containsString("datasetServerEntities\":{\"MyDataset\""));
    };

    ClientArrayFuture f1 = clientArray.executeOnAll(clientJob);
    f1.get();
    ClientArrayFuture fTms = clientArray.executeOnAll(clientJobTms);
    fTms.get();
    LOGGER.info("---> Stop");
  }

  @AfterClass
  public static void tearDownStuff() throws Exception {
    if (factory != null) {
      factory.close();
    }
  }
}