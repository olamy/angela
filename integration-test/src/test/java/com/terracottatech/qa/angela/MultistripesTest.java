package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.remote.agent.SshRemoteAgentLauncher;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;

import org.junit.Ignore;
import org.junit.Test;

import java.net.InetAddress;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class MultistripesTest {

  @Test
  public void test2StripesSsh() throws Exception {
    InetAddress local = InetAddress.getLocalHost();
    TcConfig tcConfig1 = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml"));
    tcConfig1.updateServerHost(0, local.getHostName());
    TcConfig tcConfig2 = tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml"));
    tcConfig2.updateServerHost(0, local.getHostAddress());
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig1,
        tcConfig2);
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    System.setProperty("tc.qa.angela.ssh.strictHostKeyChecking", "false");
    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2StripesSsh", new SshRemoteAgentLauncher())) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
    } finally {
      System.clearProperty("tc.qa.angela.ssh.strictHostKeyChecking");
    }
  }

  @Test
  public void test2Stripes() throws Exception {
    Topology topology = new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes1.xml")),
        tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-multistripes2.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("MultistripesTest::test2Stripes")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll();
    }
  }
}
