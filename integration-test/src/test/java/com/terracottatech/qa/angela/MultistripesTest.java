package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.TmsConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Test;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class MultistripesTest {

  @Test
  public void test2Stripes() throws Exception {
    Topology topology = new Topology(distribution(version("10.2.0.0.53"), PackageType.KIT, LicenseType.TC_DB),
        TmsConfig.noTms(),
        tcConfig(version("10.2.0.0.53"), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml")),
        tcConfig(version("10.2.0.0.53"), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
    }
  }
}
