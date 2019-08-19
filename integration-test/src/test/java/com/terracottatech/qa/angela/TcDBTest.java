package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClientArrayFuture;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.cluster.Barrier;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
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

import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
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
    final int clientCount = 2;
    final License license = new License(getClass().getResource("/terracotta/10/Terracotta101.xml"));
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
            tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(license)
        ).clientArray(clientArray -> clientArray.license(license)
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig().hostSerie(clientCount, "localhost")))
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