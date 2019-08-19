package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.test.Versions;

import org.junit.Rule;
import org.junit.Test;
import java.nio.file.Path;
import org.junit.rules.TemporaryFolder;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.security.test.util.SecurityRootDirectoryBuilder;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.security.test.util.SecurityTestUtil.StoreCharacteristic.VALID;

public class MultiStripeWithSecurityTest {

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void test2StripesWithSecurity() throws Exception {
//    System.setProperty("tc.qa.angela.skipUninstall", "true");

    Path securityRootDirectory = new SecurityRootDirectoryBuilder(temporaryFolder.newFolder())
        .withTruststore(VALID)
        .withKeystore(VALID)
        .build()
        .getPath();

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
                secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                    getClass().getResource("/terracotta/10/tc-config-multistripes1-with-security.xml"),
                    withSecurityFor(new ServerSymbolicName("Server1-1"), securityRootDirectory(securityRootDirectory)),
                    withSecurityFor(new ServerSymbolicName("Server1-2"), securityRootDirectory(securityRootDirectory))
                ),
                secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                    getClass().getResource("/terracotta/10/tc-config-multistripes2-with-security.xml"),
                    withSecurityFor(new ServerSymbolicName("Server2-1"), securityRootDirectory(securityRootDirectory)),
                    withSecurityFor(new ServerSymbolicName("Server2-2"), securityRootDirectory(securityRootDirectory))
                )
            )).license(new License(getClass().getResource("/terracotta/10/Terracotta101.xml")))
        );

    try (ClusterFactory factory = new ClusterFactory("MultiStripeWithSecurityTest::test2StripesWithSecurity", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll(securityRootDirectory(securityRootDirectory));
    }
  }
}
