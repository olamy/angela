package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.*;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.http.HttpsUtils;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.OS;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class TmsSecurityTest {

  private final static Logger logger = LoggerFactory.getLogger(TmsSecurityTest.class);
  private static final String VERSION = "10.2.0.0.365";
  private static ClusterFactory factory;
  private static TmsServerSecurityConfig TMS_SECURITY_CONFIG;
  private static final String TMS_HOSTNAME = "localhost";
  private static final String SECURITY_LEVEL = "full";
  private static final URL CLUSTER_CLIENT_SECURITY_ROOT_DIRECTORY = TmsSecurityTest.class.getResource("/terracotta/10/security-client");
  private static final URL CLIENT_TRUSTSTORE_URL = TmsSecurityTest.class.getResource("/terracotta/10/security-client/trusted-authority/trusted-authority-20180214T205001.jks");
  private static final TmsClientSecurityConfig TMS_CLIENT_SECURITY_CONFIG =  new TmsClientSecurityConfig("terracotta_security_password", CLIENT_TRUSTSTORE_URL);
  private static final OS os = new OS();
  private static Tms TMS;
  private static Tsa TSA;

  @BeforeClass
  public static void setUp() throws Exception {

    Distribution distribution = distribution(version(VERSION), PackageType.KIT, LicenseType.TC_DB);
    Topology topology = new Topology(distribution(version(VERSION), PackageType.KIT, LicenseType.TC_DB),
                                     secureTcConfig(version(VERSION),
                                                    TmsSecurityTest.class.getResource("/terracotta/10/tc-config-a-with-security.xml"),
                                                    withSecurityFor(new ServerSymbolicName("Server1"), securityRootDirectory(TmsSecurityTest.class.getResource("/terracotta/10/security-server")))));

    License license = new License(TmsSecurityTest.class.getResource("/terracotta/10/TerracottaDB101_license.xml"));

    factory = new ClusterFactory("TmsSecurityTest::testSecureConnection");
    TSA = factory.tsa(topology, license);
    TSA.installAll();
    TSA.startAll();
    TSA.licenseAll(securityRootDirectory(CLUSTER_CLIENT_SECURITY_ROOT_DIRECTORY));

    TMS_SECURITY_CONFIG = new TmsServerSecurityConfig(convertSecurityRootDirectoryPath(CLUSTER_CLIENT_SECURITY_ROOT_DIRECTORY), SECURITY_LEVEL);
    TMS = factory.tms(distribution, license, TMS_HOSTNAME, TMS_SECURITY_CONFIG);
    TMS.install();
    TMS.start();
  }

  @Test
  public void testSecureClusterConnection() throws Exception {
    String connectionName = TMS.connectToCluster(TSA.uri(), TMS_CLIENT_SECURITY_CONFIG);
    assertThat(connectionName, startsWith("TmsSecurityTest"));
  }

  @Test
  public void testSecureBrowserTmsConnection() throws Exception {
    Client client = factory.client("localhost");

    ClientJob clientJobTms = (context) -> {
      String url = "https://" + TMS_HOSTNAME + ":9480/api/connections";
      String response = HttpsUtils.sendGetRequest(url, TMS_CLIENT_SECURITY_CONFIG);
      logger.info("tms list connections result :" + response.toString());
      assertThat(response.toString(), Matchers.containsString("{}"));
    };

    Future<Void> fTms = client.submit(clientJobTms);
    fTms.get();
    logger.info("---> Stop");
  }

  @AfterClass
  public static void tearDownStuff() throws Exception {
    if (factory != null) {
      factory.close();
    }
  }

  private static String convertSecurityRootDirectoryPath(URL url) {
    if(os.isWindows()) {
      String srt = url.getFile();
      srt = srt.replaceFirst("/","");
      srt = srt.replaceAll("/","\\\\\\\\");
      return srt;
    }
    return url.getFile();
  }
}
