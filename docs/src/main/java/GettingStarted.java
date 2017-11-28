import org.junit.Test;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

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
        tcConfig(version("10.2.0-SNAPSHOT"), "/tc-config-a.xml")); // <3>
    License license = new License("/TerracottaDB101_license.xml"); // <4>

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
}
