package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClientArrayFuture;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.cluster.Barrier;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetReader;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.Record;
import com.terracottatech.store.Type;
import com.terracottatech.store.configuration.DatasetConfigurationBuilder;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_A;
import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.qa.angela.test.Versions.TERRACOTTA_VERSION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author Aurelien Broszniowski
 */

public class TcDBTest {
  private final static Logger logger = LoggerFactory.getLogger(TcDBTest.class);

  @Test
  public void testConnection() throws Exception {
    final int clientCount = 2;
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                    tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_A)
                )
            ).license(TERRACOTTA.defaultLicense())
        ).clientArray(clientArray -> clientArray
            .clientArrayTopology(
                new ClientArrayTopology(
                    distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA),
                    newClientArrayConfig().hostSerie(clientCount, "localhost")
                )
            ).license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      ClientArray clientArray = factory.clientArray();
      final URI uri = tsa.uri();
      ClientJob clientJob = (cluster) -> {
        Barrier barrier = cluster.barrier("testConnection", clientCount);
        logger.info("client started and waiting on barrier");
        int rank = barrier.await();

        logger.info("all client sync'ed");
        try (DatasetManager datasetManager = DatasetManager.clustered(uri).build()) {
          DatasetConfigurationBuilder builder = datasetManager.datasetConfiguration()
              .offheap("primary-server-resource");
          Dataset<String> dataset = null;

          if (rank == 0) {
            boolean datasetCreated = datasetManager.newDataset("MyDataset", Type.STRING, builder.build());
            if (datasetCreated) {
              logger.info("created dataset");
            }
            dataset = datasetManager.getDataset("MyDataset", Type.STRING);
          }

          barrier.await();

          if (rank != 0) {
            dataset = datasetManager.getDataset("MyDataset", Type.STRING);
            logger.info("got existing dataset");
          }

          barrier.await();

          if (rank == 0) {
            DatasetWriterReader<String> writerReader = dataset.writerReader();
            writerReader.add("ONE", CellDefinition.defineLong("val").newCell(1L));
            logger.info("Value created for key ONE");
          }

          barrier.await();

          if (rank != 0) {
            DatasetReader<String> reader = dataset.reader();
            Optional<Record<String>> one = reader.get("ONE");
            assertThat(one.isPresent(), is(true));
            logger.info("Cell value = {}", one.get());
          }

          barrier.await();

          dataset.close();
        }
        logger.info("client done");
      };

      ClientArrayFuture caf = clientArray.executeOnAll(clientJob);
      caf.get();
    }

    logger.info("---> Stop");
  }
}