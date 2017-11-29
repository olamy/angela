package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.client.Barrier;
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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
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
    Topology topology = new Topology(distribution(version("10.2.0.0.53"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.2.0.0.53"), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();

      Client client = factory.client("localhost");
      ClientJob clientJob = (context) -> {
        Barrier barrier = context.barrier("testConnection", 2);
        logger.info("client started and waiting on barrier");
        int rank = barrier.await();

        logger.info("all client sync'ed");
        try {
          try (DatasetManager datasetManager = DatasetManager.clustered(URI.create("terracotta://localhost:9510")).build()) {
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
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      };

      Future<Void> f1 = client.submit(clientJob);
      Future<Void> f2 = client.submit(clientJob);
      f1.get();
      f2.get();
    }

    logger.info("---> Stop");
  }
}