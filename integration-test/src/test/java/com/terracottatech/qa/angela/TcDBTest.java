package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tms;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.client.Barrier;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetReader;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.Record;
import com.terracottatech.store.Type;
import com.terracottatech.store.configuration.DatasetConfigurationBuilder;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import com.terracottatech.store.setting.ReadVisibility;
import com.terracottatech.store.setting.WriteVisibility;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.TmsConfig.withTms;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author Aurelien Broszniowski
 */

public class TcDBTest {

  private final static Logger logger = LoggerFactory.getLogger(TcDBTest.class);

  @Test
  public void testConnection() throws Exception {
    Distribution distribution = distribution(version("10.2.0.0.129"), PackageType.KIT, LicenseType.TC_DB);
    Topology topology = new Topology(distribution,
        tcConfig(version("10.2.0.0.129"), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
      String tmsHostname = "localhost";
      Tms tms = factory.tms(distribution, license, tmsHostname);
      tms.install();
      tms.start();
      tms.connectToCluster(tsa.clusterURI());
      Client client = factory.client("localhost");
      ClientJob clientJob = (context) -> {
        Barrier barrier = context.barrier("testConnection", 2);
        logger.info("client started and waiting on barrier");
        int rank = barrier.await();

        logger.info("all client sync'ed");
        try (DatasetManager datasetManager = DatasetManager.clustered(context.tsaURI()).build()) {
          DatasetConfigurationBuilder builder = datasetManager.datasetConfiguration()
              .offheap("primary-server-resource");
          Dataset<String> dataset = null;

          if (rank == 0) {
            dataset = datasetManager.createDataset("MyDataset", Type.STRING, builder.build());
            logger.info("created dataset");
          }

          barrier.await();

          if (rank != 0) {
            dataset = datasetManager.getDataset("MyDataset", Type.STRING);
            logger.info("got existing dataset");
          }

          barrier.await();

          if (rank == 0) {
            DatasetWriterReader<String> writerReader = dataset.writerReader(ReadVisibility.ROUTINE.asReadSettings(), WriteVisibility.IMMEDIATE
                .asWriteSettings());
            writerReader.add("ONE", CellDefinition.defineLong("val").newCell(1L));
            logger.info("Value created for key ONE");
          }

          barrier.await();

          if (rank != 0) {
            DatasetReader<String> reader = dataset.reader(ReadVisibility.ROUTINE.asReadSettings());
            Optional<Record<String>> one = reader.get("ONE");
            assertThat(one.isPresent(), is(true));
            logger.info("Cell value = {}", one.get());
          }

          barrier.await();

          dataset.close();
        }
        logger.info("client done");
      };

      ClientJob clientJobTms = (context) -> {
        try {
          String url = "http://" + tmsHostname + ":9480/api/connections";
          URL obj = new URL(url);
          HttpURLConnection con = (HttpURLConnection) obj.openConnection();
          int responseCode = con.getResponseCode();
          BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
          String inputLine;
          StringBuilder response = new StringBuilder();
          while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
          }
          in.close();
          logger.info("tms list connections result :" + response.toString());
          assertThat(response.toString(), Matchers.containsString("datasetServerEntities\":{\"MyDataset\""));
        } catch (Exception e) {

        }
      };

      Future<Void> f1 = client.submit(clientJob);
      Future<Void> f2 = client.submit(clientJob);
      f1.get();
      f2.get();

      Future<Void> fTms = client.submit(clientJobTms);
      fTms.get();
    }

    logger.info("---> Stop");
  }
}