package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tms;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.http.HttpUtils;
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
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TmsSecurityTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(TmsSecurityTest.class);

  private static ClusterFactory factory;
  private static final String TMS_HOSTNAME = "localhost";

  private static URI clientTruststoreUri;
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

    clientTruststoreUri = clientSecurityRootDirectory.getTruststorePaths().iterator().next().toUri();

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

    TmsServerSecurityConfig securityConfig = new TmsServerSecurityConfig.Builder()
        .with(config -> {
              config.tmsSecurityRootDirectory = clientSecurityRootDirectory.getPath().toString();
              config.tmsSecurityRootDirectoryConnectionDefault = serverSecurityRootDirectory.getPath().toString();
              config.tmsSecurityHttpsEnabled = "true";
            }
        ).build();

    TMS = factory.tms(distribution, license, TMS_HOSTNAME, securityConfig);
    TMS.install();
    TMS.start();
  }

  @Test
  public void could_create_connection_to_secure_cluster_test() {
    TmsClientSecurityConfig tmsClientSecurityConfig = new TmsClientSecurityConfig("terracotta_security_password", clientTruststoreUri);
    String connectionName = TMS.connectToCluster(TSA.uri(), tmsClientSecurityConfig);
    assertThat(connectionName, startsWith("TmsSecurityTest"));
  }

  @Test
  public void http_client_connects_to_tms_using_ssl_test() throws Exception {
    TmsClientSecurityConfig tmsClientSecurityConfig = new TmsClientSecurityConfig("terracotta_security_password", clientTruststoreUri);
    Client client = factory.client("localhost");

    ClientJob clientJobTms = (context) -> {
      String url = "https://" + TMS_HOSTNAME + ":9480/api/connections";
      String response = HttpUtils.sendGetRequest(url, tmsClientSecurityConfig);
      LOGGER.info("tms list connections result :" + response);
      assertThat(response, Matchers.containsString("TmsSecurityTest"));

      String infoUrl = "https://" + TMS_HOSTNAME + ":9480/api/info";
      String infoResponse = HttpUtils.sendGetRequest(infoUrl, tmsClientSecurityConfig);
      LOGGER.info("tms info :" + response);

      assertThat(infoResponse, Matchers.containsString("\"connection_certificateAuthenticationEnabled\":true"));
      assertThat(infoResponse, Matchers.containsString("\"connection_secured\":true"));
      assertThat(infoResponse, Matchers.containsString("\"connection_sslEnabled\":true"));
      assertThat(infoResponse, Matchers.containsString("\"connection_certificateAuthenticationEnabled\":true"));
      assertThat(infoResponse, Matchers.containsString("\"connection_hasPasswordToPresent\":false"));

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
