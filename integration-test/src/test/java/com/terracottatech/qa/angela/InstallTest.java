package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {

  @Test
  public void testLocallInstall() throws Exception {
    Topology topology = new Topology(distribution(version("10.2.0.0.53"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.2.0.0.53"), "/terracotta/10/tc-config-a.xml"));
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
    }
  }

  @Test
  public void testStopPassive() throws Exception {
    Topology topology = new Topology(distribution(version("10.2.0.0.53"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.2.0.0.53"), "/terracotta/10/tc-config-ap.xml"));
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();

      TerracottaServer passive = tsa.getSinglePassive();
      tsa.stop(passive);

      assertThat(tsa.getState(passive), is(TerracottaServerState.STOPPED));
      assertThat(tsa.getSinglePassive(), is(nullValue()));
    }
  }


}
