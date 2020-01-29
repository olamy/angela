/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

import org.terracotta.angela.client.ClientArray;
import org.terracotta.angela.client.ClientArrayFuture;
import org.terracotta.angela.client.ClusterFactory;
import org.terracotta.angela.client.Tsa;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.common.TerracottaServerState;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.tcconfig.ServerSymbolicName;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.topology.ClientArrayTopology;
import org.terracotta.angela.common.topology.LicenseType;
import org.terracotta.angela.common.topology.PackageType;
import org.terracotta.angela.common.topology.Topology;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.Callable;

import static org.terracotta.angela.client.config.custom.CustomConfigurationContext.customConfigurationContext;
import static org.terracotta.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static org.terracotta.angela.common.distribution.Distribution.distribution;
import static org.terracotta.angela.common.tcconfig.NamedSecurityRootDirectory.withSecurityFor;
import static org.terracotta.angela.common.tcconfig.SecureTcConfig.secureTcConfig;
import static org.terracotta.angela.common.tcconfig.SecurityRootDirectory.securityRootDirectory;
import static org.terracotta.angela.common.tcconfig.TcConfig.tcConfig;
import static org.terracotta.angela.common.topology.Version.version;
import static org.terracotta.angela.test.Versions.TERRACOTTA_VERSION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Aurelien Broszniowski
 */
public class GettingStarted {
  private static final License LICENSE = LicenseType.TERRACOTTA.defaultLicense();

  @Test
  public void configureCluster() throws Exception {
    // tag::configureCluster[]
    ConfigurationContext configContext = customConfigurationContext() // <1>
        .tsa(tsa -> tsa // <2>
            .topology(new Topology( // <3>
                distribution(version(TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), // <4>
                tcConfig(version(TERRACOTTA_VERSION), getClass().getResource("/tc-config-a.xml")))) // <5>
            .license(LICENSE) // <6>
        );

    ClusterFactory factory = new ClusterFactory("GettingStarted::configureCluster", configContext); // <7>
    Tsa tsa = factory.tsa() // <8>
        .startAll() // <9>
        .licenseAll(); // <10>

    factory.close(); // <11>
    // end::configureCluster[]
  }

  @Test
  public void showTsaApi() throws Exception {
    Topology topology = new Topology(
        distribution(version(TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
        tcConfig(version(TERRACOTTA_VERSION), getClass().getResource("/tc-config-ap.xml"))
    );
    ConfigurationContext configContext = customConfigurationContext().tsa(tsa -> tsa.topology(topology).license(LICENSE));

    try (ClusterFactory factory = new ClusterFactory("GettingStarted::showTsaApi", configContext)) {
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
      Callable<TerracottaServerState> serverState = () -> tsa.getState(passive); // <11>
      Awaitility.await()
          .pollInterval(1, SECONDS)
          .atMost(15, SECONDS)
          .until(serverState, is(TerracottaServerState.STARTED_AS_ACTIVE));
      // end::showTsaApi[]
    }
  }

  @Test
  public void runClient() throws Exception {
    // tag::runClient[]
    ConfigurationContext configContext = customConfigurationContext()
        .clientArray(clientArray -> clientArray // <1>
            .license(LICENSE) // <2>
            .clientArrayTopology(new ClientArrayTopology( // <3>
                distribution(version(TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), // <4>
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
