import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClientArrayFuture;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import org.junit.Assert;
import org.junit.Test;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.util.Collection;

import static com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext.*;
import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static com.terracottatech.qa.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class GettingStarted {

  @Test
  public void configureCluster() throws Exception {
    // tag::configureCluster[]
    ConfigurationContext configContext = customConfigurationContext() // <1>
        .tsa(tsa -> tsa // <2>
            .topology(new Topology( // <3>
                distribution(version("10.3.0.1.80"), PackageType.KIT, LicenseType.TERRACOTTA), // <4>
                tcConfig(version("10.3.0.1.80"), getClass().getResource("/tc-config-a.xml")))) // <5>
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"))) // <6>
        );

    ClusterFactory factory = new ClusterFactory("GettingStarted::configureCluster", configContext); // <7>
    Tsa tsa = factory.tsa() // <8>
        .startAll() // <9>
        .licenseAll(); // <10>

    factory.close(); // <11>
    // end::configureCluster[]
  }

  @Test
  public void configureClusterWithSecurity() throws Exception {
    // tag::configureClusterWithSecurity[]
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version("10.3.0.1.80"), PackageType.KIT, LicenseType.TERRACOTTA), // <1>
                secureTcConfig(version("10.3.0.1.80"), getClass().getResource("/tc-config-a-with-security.xml"), // <2>
                    withSecurityFor(new ServerSymbolicName("Server1"), securityRootDirectory(getClass().getResource("/security"))))) // <3>
            ).license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );
    ClusterFactory factory = new ClusterFactory("GettingStarted::configureClusterWithSecurity", configContext); // <4>
    Tsa tsa = factory.tsa() // <5>
        .startAll()
        .licenseAll(securityRootDirectory(getClass().getResource("/security"))); // <6>

    factory.close();
    // end::configureClusterWithSecurity[]
  }

  @Test
  public void showTsaApi() throws Exception {
    Topology topology = new Topology(
        distribution(version("10.3.0.1.80"), PackageType.KIT, LicenseType.TERRACOTTA),
        tcConfig(version("10.3.0.1.80"), getClass().getResource("/tc-config-ap.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(topology)
            .license(license)
        );
    try (ClusterFactory factory = new ClusterFactory("GettingStarted::configureCluster", configContext)) {
      // tag::showTsaApi[]
      Tsa tsa = factory.tsa() // <1>
          .startAll() // <2>
          .licenseAll(); // <3>

      TerracottaServer active = tsa.getActive(); // <4>
      Collection<TerracottaServer> actives = tsa.getActives(); // <5>
      TerracottaServer passive = tsa.getPassive(); // <6>
      Collection<TerracottaServer> passives = tsa.getPassives(); // <7>

      tsa.stopAll(); // <8>

      tsa.start(active); // <9>
      tsa.start(passive);

      tsa.stop(active); // <10>
      TerracottaServerState state = tsa.getState(passive); // <11>
      Assert.assertEquals(TerracottaServerState.STARTED_AS_ACTIVE, state);
      // end::showTsaApi[]
    }
  }

  @Test
  public void runClient() throws Exception {
    // tag::runClient[]
    ConfigurationContext configContext = customConfigurationContext()
        .clientArray(clientArray -> clientArray // <1>
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"))) // <2>
            .clientArrayTopology(new ClientArrayTopology( // <3>
                distribution(version("10.3.0.1.80"), PackageType.KIT, LicenseType.TERRACOTTA), // <4>
                newClientArrayConfig().host("localhost-1", "localhost").host("localhost-2", "localhost")) // <5>
            )
        );
    ClusterFactory factory = new ClusterFactory("GettingStarted::runClient", configContext);
    ClientArray clientArray = factory.clientArray(); // <6>
    ClientArrayFuture f = clientArray.executeOnAll((context) -> System.out.println("Hello")); // <7>
    f.get(); // <8>

    factory.close();
    // end::runClient[]
  }

}
