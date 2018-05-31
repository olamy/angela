package com.terracottatech.qa.angela;

import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tms;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.http.HttpsUtils;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import com.terracottatech.security.test.util.SecurityRootDirectory;
import com.terracottatech.security.test.util.SecurityRootDirectoryBuilder;

import java.net.URI;
import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.security.test.util.SecurityTestUtil.StoreCharacteristic.VALID;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@Ignore("TDB-3370")
public class TmsSecurityTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(TmsSecurityTest.class);

  private static ClusterFactory factory;
  private static final String TMS_HOSTNAME = "localhost";

  private static TmsClientSecurityConfig tmsClientSecurityConfig;
  private static Tms TMS;
  private static Tsa TSA;

  @ClassRule
  public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  @BeforeClass
  public static void setUp() throws Exception {
    SecurityRootDirectory clientSecurityRootDirectory = new SecurityRootDirectoryBuilder(TEMPORARY_FOLDER.newFolder())
        .withTruststore(VALID)
        .withKeystore(VALID)
        .build();
    SecurityRootDirectory serverSecurityRootDirectory = new SecurityRootDirectoryBuilder(TEMPORARY_FOLDER.newFolder())
        .withTruststore(VALID)
        .withKeystore(VALID)
        .build();

    URI clientTruststoreUri = clientSecurityRootDirectory.getTruststorePaths().iterator().next().toUri();
    tmsClientSecurityConfig = new TmsClientSecurityConfig("terracotta_security_password", clientTruststoreUri);

    Distribution distribution = distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TC_DB);
    Topology topology =
        new Topology(
            distribution(
                version(Versions.TERRACOTTA_VERSION),
                PackageType.KIT, LicenseType.TC_DB
            ),
            secureTcConfig(
                version(Versions.TERRACOTTA_VERSION),
                TmsSecurityTest.class.getResource("/terracotta/10/tc-config-a-with-security.xml"),
                withSecurityFor(new ServerSymbolicName("Server1"), securityRootDirectory(serverSecurityRootDirectory.getPath()))
            ));

    License license = new License(TmsSecurityTest.class.getResource("/terracotta/10/TerracottaDB101_license.xml"));

    factory = new ClusterFactory("TmsSecurityTest::testSecureConnection");
    TSA = factory.tsa(topology, license);
    TSA.installAll();
    TSA.startAll();
    TSA.licenseAll(securityRootDirectory(clientSecurityRootDirectory.getPath()));

    TmsServerSecurityConfig securityConfig = new TmsServerSecurityConfig(clientSecurityRootDirectory.getPath(), serverSecurityRootDirectory.getPath());
    TMS = factory.tms(distribution, license, TMS_HOSTNAME, securityConfig);
    TMS.install();
    TMS.start();
  }

  @Test
  public void testSecureClusterConnection() throws Exception {
    String connectionName = TMS.connectToCluster(TSA.uri(), tmsClientSecurityConfig);
    assertThat(connectionName, startsWith("TmsSecurityTest"));
  }

  @Test
  public void testSecureBrowserTmsConnection() throws Exception {
    Client client = factory.client("localhost");

    ClientJob clientJobTms = (context) -> {
      String url = "https://" + TMS_HOSTNAME + ":9480/api/connections";
      String response = HttpsUtils.sendGetRequest(url, tmsClientSecurityConfig);
      LOGGER.info("tms list connections result :" + response);
      assertThat(response, Matchers.containsString("{}"));
    };

    Future<Void> fTms = client.submit(clientJobTms);
    fTms.get();
    LOGGER.info("---> Stop");
  }

  @AfterClass
  public static void tearDownStuff() throws Exception {
    if (factory != null) {
      factory.close();
    }
  }
}
