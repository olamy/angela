package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import com.terracottatech.security.test.util.SecurityRootDirectoryBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static com.terracottatech.qa.angela.TestUtils.LICENSE;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.security.test.util.SecurityTestUtil.StoreCharacteristic.VALID;

/**
 * @author Aurelien Broszniowski
 */

public class InstallWithSecurityTest {
  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testLocalInstallWithSecurity() throws Exception {
    Path securityRootDirectory = new SecurityRootDirectoryBuilder(temporaryFolder.newFolder())
        .withTruststore(VALID)
        .withKeystore(VALID)
        .build()
        .getPath();

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION),
            PackageType.KIT, LicenseType.TERRACOTTA),
            secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                getClass().getResource("/terracotta/10/tc-config-a-with-security.xml"),
                withSecurityFor(new ServerSymbolicName("Server1"), securityRootDirectory(securityRootDirectory)))))
            .license(LICENSE)
        );

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection", configContext)) {
      factory.tsa()
          .startAll()
          // client and server run on localhost, re-using server's certificate
          .licenseAll(securityRootDirectory(securityRootDirectory));
    }
  }

  @Test
  public void testLocalInstallAPWithSecurity() throws Exception {
    Path securityRootDirectory = new SecurityRootDirectoryBuilder(temporaryFolder.newFolder())
        .withTruststore(VALID)
        .withKeystore(VALID)
        .build()
        .getPath();

    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION),
            PackageType.KIT, LicenseType.TERRACOTTA),
            secureTcConfig(version(Versions.TERRACOTTA_VERSION),
                getClass().getResource("/terracotta/10/tc-config-ap-with-security.xml"),
                withSecurityFor(new ServerSymbolicName("Server1"), securityRootDirectory(securityRootDirectory)),
                withSecurityFor(new ServerSymbolicName("Server2"), securityRootDirectory(securityRootDirectory)))))
            .license(LICENSE)
        );

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection", configContext)) {
      factory.tsa()
          .startAll()
          // client and server run on localhost, re-using server's certificate
          .licenseAll(securityRootDirectory(securityRootDirectory));
    }
  }
}
