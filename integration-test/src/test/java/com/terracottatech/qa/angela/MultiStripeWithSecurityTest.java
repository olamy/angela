package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

public class MultiStripeWithSecurityTest {

  private static final String VERSION = "10.2.0.0.365";

  @Test
  public void test2StripesWithSecurity() throws Exception {
    Topology topology =
        new Topology(distribution(version(VERSION), PackageType.KIT, LicenseType.TC_DB),
                     secureTcConfig(version(VERSION),
                                    getClass().getResource("/terracotta/10/tc-config-multistripes1-with-security.xml"),
                                    withSecurityFor(new ServerSymbolicName("Server1-1"),
                                                    securityRootDirectory(getClass().getResource("/terracotta/10/security")))),
                     secureTcConfig(version(VERSION),
                                    getClass().getResource("/terracotta/10/tc-config-multistripes2-with-security.xml"),
                                    withSecurityFor(new ServerSymbolicName("Server1-2"),
                                                    securityRootDirectory(getClass().getResource("/terracotta/10/security")))));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("MultiStripeWithSecurityTest::test2StripesWithSecurity")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      tsa.licenseAll(securityRootDirectory(getClass().getResource("/terracotta/10/security")));
    }
  }
}
