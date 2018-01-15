package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tms;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.Type;
import com.terracottatech.store.configuration.DatasetConfigurationBuilder;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.http.HttpUtils.sendGetRequest;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Aurelien Broszniowski
 */

public class TmsTest {

  private final static Logger logger = LoggerFactory.getLogger(TmsTest.class);

  @Test
  public void testConnection() throws Exception {
    Distribution distribution = distribution(version("10.2.0.0.144"), PackageType.KIT, LicenseType.TC_DB);
    Topology topology = new Topology(distribution,
        tcConfig(version("10.2.0.0.144"), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("TmsTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
      String tmsHostname = "localhost";
      Tms tms = factory.tms(distribution, license, tmsHostname);
      tms.install();
      tms.start();
      tms.connectToCluster(tsa.uri());
      Client client = factory.client("localhost");
      ClientJob clientJob = (context) -> {
        try (DatasetManager datasetManager = DatasetManager.clustered(context.tsaURI()).build()) {
          DatasetConfigurationBuilder builder = datasetManager.datasetConfiguration()
              .offheap("primary-server-resource");
          boolean datasetCreated = datasetManager.createDataset("MyDataset", Type.STRING, builder.build());
          if (datasetCreated) {
            logger.info("created dataset");
          }
          try (Dataset<String> dataset = datasetManager.getDataset("MyDataset", Type.STRING)) {
            DatasetWriterReader<String> writerReader = dataset.writerReader();
            writerReader.add("ONE", CellDefinition.defineLong("val").newCell(1L));
            logger.info("Value created for key ONE");
            dataset.close();
          }

        }
        logger.info("client done");
      };

      ClientJob clientJobTms = (context) -> {
        String url = "http://" + tmsHostname + ":9480/api/connections";
        String response = sendGetRequest(url);
        logger.info("tms list connections result :" + response.toString());
        assertThat(response.toString(), Matchers.containsString("datasetServerEntities\":{\"MyDataset\""));
      };

      Future<Void> f1 = client.submit(clientJob);
      f1.get();
      Future<Void> fTms = client.submit(clientJobTms);
      fTms.get();
    }

    logger.info("---> Stop");
  }
}