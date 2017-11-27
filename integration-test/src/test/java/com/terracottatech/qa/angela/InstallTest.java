package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Instance;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Test;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {

  @Test
  public void testLocallInstall() throws Exception {
    Topology topology = new Topology(distribution(version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-a.xml"));
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (Instance instance = new Instance("TcDBTest::testConnection")) {
      Tsa tsa = instance.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

}
