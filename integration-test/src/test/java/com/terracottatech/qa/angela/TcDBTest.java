package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.common.client.Barrier;
import com.terracottatech.store.StoreException;
import org.junit.Test;

import com.terracottatech.qa.angela.client.ClientControl;
import com.terracottatech.qa.angela.client.TsaControl;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.Type;
import com.terracottatech.store.configuration.DatasetConfigurationBuilder;
import com.terracottatech.store.manager.DatasetManager;

import java.net.URI;
import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class TcDBTest {
  @Test
  public void testConnection() throws Exception {
    TcConfig tcConfig = new TcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-a.xml");

    Topology topology = new Topology("1", Distribution.distribution(version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB), tcConfig);
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (TsaControl control = new TsaControl(topology, license)) {
      control.installAll();
      control.startAll();
      control.licenseAll();

      System.out.println("---> Wait for 3 sec");
      Thread.sleep(3000);

      try (ClientControl clientControl = control.clientControl("localhost")) {
        ClientJob clientJob = (context) -> {
          Barrier barrier = context.barrier("testConnection", 2);
          System.out.println("client started and waiting on barrier");
          barrier.await();

          System.out.println("all client sync'ed");
          try {
            DatasetManager datasetManager = DatasetManager.clustered(URI.create("terracotta://localhost:9510")).build();
            DatasetConfigurationBuilder builder = datasetManager.datasetConfiguration()
                .offheap("primary-server-resource");
            Dataset<String> dataset;
            try {
              dataset = datasetManager.createDataset("MyDataset", Type.STRING, builder.build());
              System.out.println("created dataset");
            } catch (StoreException se) {
              dataset = datasetManager.getDataset("MyDataset", Type.STRING);
              System.out.println("got existing dataset");
            }

            // We managed to connect

            dataset.close();
            datasetManager.close();
            System.out.println("client done");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

        Future<Void> f1 = clientControl.submit(clientJob);
        Future<Void> f2 = clientControl.submit(clientJob);
        f1.get();
        f2.get();
      }

      System.out.println("---> Stop");
      control.stopAll();
    }
  }
}