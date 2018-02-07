package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClusterFactory;
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
    Topology topology = new Topology(distribution(version("10.2.0.0.224"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.2.0.0.224"), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory instance = new ClusterFactory("InstallTest::testRemoteInstall")) {
      Tsa tsa = instance.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();

      try (Client client = instance.client("localhost")) {
        Future<Void> f = client.submit((context) -> System.out.println("hello world"));
        f.get();
      }
    }
  }
}
