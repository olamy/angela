package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import org.junit.Assert;
import org.junit.Test;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.net.ClientToServerDisruptor;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.StoreOperationAbandonedException;
import com.terracottatech.store.StoreReconnectFailedException;
import com.terracottatech.store.Type;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 *
 */
public class ClientToServerDisruptionTest {

  private static final CellDefinition<Integer> CELL_1 = CellDefinition.defineInt("cell1");

  /**
   * Create partition between client and server and verify store operation throws exeception after reconnect attempts get
   * timed out(5 seconds).
   */
  @Test
  public void testReconnectTimeout() throws Exception {
    //set netDisruptionEnabled to true for enabling disruption.
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), true,
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a-short-lease.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"))));

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection", config)) {
      try (Tsa tsa = factory.tsa().startAll().licenseAll()) {
        try (ClientToServerDisruptor disruptor = tsa.disruptionController().newClientToServerDisruptor()) {
          //use proxied URI from disruptor to make connection
          DatasetManager datasetManager = DatasetManager.clustered(disruptor.uri())
              .withConnectionTimeout(5, TimeUnit.SECONDS)
              .withReconnectTimeout(20, TimeUnit.SECONDS)
              .build();
          Assert.assertTrue(datasetManager.newDataset("store-0", Type.INT, datasetManager.datasetConfiguration()
              .offheap("primary-server-resource").build()));
          Dataset<Integer> dataset = datasetManager.getDataset("store-0", Type.INT);
          DatasetWriterReader<Integer> writerReader = dataset.writerReader();
          Assert.assertTrue(writerReader.add(1, CELL_1.newCell(1)));
          //partition between client and server
          disruptor.disrupt();
          //verify store operations throws exception after reconnect timeout
          try {
            writerReader.add(2, CELL_1.newCell(2));
            Assert.fail("expected StoreReconnectFailedException not thrown");
          } catch (StoreReconnectFailedException e) {
            //expected
          }

        }
      }

    }
  }

  /**
   * Verify store operation can resume after reconnect.
   */
  @Test
  public void testResumeOperationsAfterReconnect() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB), true,
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a-short-lease.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection", config)) {
      try (Tsa tsa = factory.tsa().startAll()) {
        tsa.startAll();
        tsa.licenseAll();
        try (ClientToServerDisruptor disruptor = tsa.disruptionController().newClientToServerDisruptor()) {
          try (DatasetManager datasetManager = DatasetManager.clustered(disruptor.uri())
              .withConnectionTimeout(5, TimeUnit.SECONDS)
              .withReconnectTimeout(20, TimeUnit.SECONDS)
              .build()) {
            System.out.println("before newDataset");
            Assert.assertTrue(datasetManager.newDataset("store-0", Type.INT, datasetManager.datasetConfiguration()
                .offheap("primary-server-resource").build()));
            Dataset<Integer> dataset = datasetManager.getDataset("store-0", Type.INT);
            DatasetWriterReader<Integer> writerReader = dataset.writerReader();
            Assert.assertTrue(writerReader.add(1, CELL_1.newCell(1)));

            //parition client and server and stop it before reconnect timeout
            disruptor.disrupt();
            CompletableFuture.runAsync(() -> {
              try {
                Thread.sleep(10_000);
              } catch (InterruptedException e) {}
              disruptor.undisrupt();
            });

            //store operation during partition and verify it first throws StoreOperationAbandonedException after
            //network restored and then it can resume upon resubmit.
            try {
              writerReader.add(2, CELL_1.newCell(2));
              Assert.fail("expected StoreOperationAbandonedException not thrown");
            } catch (StoreOperationAbandonedException e) {
              //this exception expected as reconnect happened during this operation.
              writerReader.add(2, CELL_1.newCell(2));
            }

          }
        }
      }

    }
  }


}
