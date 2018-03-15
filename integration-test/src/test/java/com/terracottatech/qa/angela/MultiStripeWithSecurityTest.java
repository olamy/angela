package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.test.Versions;
import org.junit.Test;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.topology.Version.version;

public class MultiStripeWithSecurityTest {

  @Test
  public void test2StripesWithSecurity() throws Exception {
    Topology topology =
        new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB),
                     secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                                    getClass().getResource("/terracotta/10/tc-config-multistripes1-with-security.xml"),
                                    withSecurityFor(new ServerSymbolicName("Server1-1"),
                                                    securityRootDirectory(getClass().getResource("/terracotta/10/security")))),
                     secureTcConfig(version(Versions.TERRACOTTA_VERSION),
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
