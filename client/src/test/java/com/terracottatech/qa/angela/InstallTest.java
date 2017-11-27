package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.Instance;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Test;

import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {

  @Test
  public void testRemoteInstall() throws Exception {
    Topology topology = new Topology(distribution(version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-a.xml"));
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (Instance instance = new Instance("InstallTest::testRemoteInstall")) {
      Tsa tsa = instance.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();

      System.out.println("---> Wait for 3 sec");
      Thread.sleep(3000);

      try (Client client = instance.client("localhost")) {
        Future<Void> f = client.submit((context) -> System.out.println("hello dudes 5"));
        f.get();
      }

      System.out.println("---> Stop");
      tsa.stopAll();
    }
  }
}
