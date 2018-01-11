import com.terracottatech.qa.angela.common.topology.TmsConfig;
import org.junit.Assert;
import org.junit.Test;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.util.Collection;
import java.util.concurrent.Future;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class GettingStarted {

  @Test
  public void configureCluster() throws Exception {
    // tag::configureCluster[]
    Topology topology = new Topology( // <1>
        distribution(version("10.2.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB), // <2>
        TmsConfig.noTms(),
        tcConfig(version("10.2.0-SNAPSHOT"), getClass().getResource("/tc-config-a.xml"))); // <3>
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")); // <4>

    ClusterFactory factory = new ClusterFactory("GettingStarted::configureCluster");   // <5>
    Tsa tsa = factory.tsa(topology, license); // <5>
    tsa.installAll(); // <6>
    tsa.startAll(); // <7>
    tsa.licenseAll(); // <8>

    factory.close(); // <9>
    // end::configureCluster[]
  }

  @Test
  public void runClient() throws Exception {
    // tag::runClient[]
    ClusterFactory factory = new ClusterFactory("GettingStarted::runClient");
    Client client = factory.client("localhost"); // <1>
    Future<Void> f = client.submit((context) -> System.out.println("Hello")); // <2>
    f.get(); // <3>

    factory.close();
    // end::runClient[]
  }

  @Test
  public void showTsaApi() throws Exception {
    Topology topology = new Topology(
        distribution(version("10.2.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB),
        TmsConfig.noTms(),
        tcConfig(version("10.2.0-SNAPSHOT"), getClass().getResource("/tc-config-ap.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    try (ClusterFactory factory = new ClusterFactory("GettingStarted::configureCluster")) {
      Tsa tsa = factory.tsa(topology, license);
      // tag::showTsaApi[]
      tsa.installAll(); // <1>
      tsa.startAll(); // <2>
      tsa.licenseAll(); // <3>

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
}
