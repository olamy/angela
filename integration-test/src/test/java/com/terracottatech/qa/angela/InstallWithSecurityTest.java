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

/**
 * @author Aurelien Broszniowski
 */

public class InstallWithSecurityTest {

  @Test
  public void testLocalInstallWithSecurity() throws Exception {
    Topology topology =
        new Topology(distribution(version(Versions.TERRACOTTA_VERSION),
                                  PackageType.KIT, LicenseType.TC_DB),
                     secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                                    getClass().getResource("/terracotta/10/tc-config-a-with-security.xml"),
                                    withSecurityFor(new ServerSymbolicName("Server1"),
                                                    securityRootDirectory(getClass().getResource("/terracotta/10/security")))));

    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      // client and server run on localhost, re-using server's certificate
      tsa.licenseAll(securityRootDirectory(getClass().getResource("/terracotta/10/security")));
    }
  }

  @Test
  public void testLocalInstallAPWithSecurity() throws Exception {
    Topology topology =
        new Topology(distribution(version(Versions.TERRACOTTA_VERSION),
                                  PackageType.KIT, LicenseType.TC_DB),
                     secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                                    getClass().getResource("/terracotta/10/tc-config-ap-with-security.xml"),
                                    withSecurityFor(new ServerSymbolicName("Server1"),
                                                    securityRootDirectory(getClass().getResource("/terracotta/10/security"))
                                    ),
                                    withSecurityFor(new ServerSymbolicName("Server2"),
                                                    securityRootDirectory(getClass().getResource("/terracotta/10/security")))));

    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();
      // client and server run on localhost, re-using server's certificate
      tsa.licenseAll(securityRootDirectory(getClass().getResource("/terracotta/10/security")));
    }
  }
}
