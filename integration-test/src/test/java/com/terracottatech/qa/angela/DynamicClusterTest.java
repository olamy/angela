package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext.customConfigurationContext;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.dynamic_cluster.Stripe.stripe;
import static com.terracottatech.qa.angela.common.provider.DynamicConfigManager.dynamicCluster;
import static com.terracottatech.qa.angela.common.tcconfig.TerracottaServer.server;
import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@org.junit.Ignore("TDB-4771: Ignore until dynamic-config kits are published to Kratos")
public class DynamicClusterTest {
  @BeforeClass
  public static void beforeClass() {
    System.setProperty("angela.rootDir", "/Users/saag/angela");
  }

  @Test
  public void testNodeStartup() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testNodeStartup", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(2));
      assertThat(tsa.getStarted().size(), is(2));
    }
  }

  @Test
  public void testDynamicNodeAttachToSingleNodeStripe() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testDynamicNodeAttachToSingleNodeStripe", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));

      tsa.attachNodes(0, server("server-2", "localhost")
          .tsaPort(9510)
          .tsaGroupPort(9511)
          .configRepo("terracotta2/repository")
          .logs("terracotta2/logs")
          .metaData("terracotta2/metadata"));

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(2));
    }
  }

  @Test
  public void testDynamicNodeAttachToMultiNodeStripe() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testDynamicNodeAttachToMultiNodeStripe", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(2));

      tsa.attachNodes(0, server("server-3", "localhost")
          .tsaPort(9610)
          .tsaGroupPort(9611)
          .configRepo("terracotta3/repository")
          .logs("terracotta3/logs")
          .metaData("terracotta3/metadata"));

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(3));
    }
  }

  @Test
  public void testDynamicStripeAttachToSingleStripeCluster() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testDynamicStripeAttachToSingleStripeCluster", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));

      tsa.attachStripe(server("server-2", "localhost")
          .tsaPort(9510)
          .tsaGroupPort(9511)
          .configRepo("terracotta2/repository")
          .logs("terracotta2/logs")
          .metaData("terracotta2/metadata"));

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(1).size(), is(1));
    }
  }

  @Test
  public void testDynamicStripeAttachToMultiStripeCluster() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata")
                        ),
                        stripe(
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testDynamicStripeAttachToMultiStripeCluster", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(1).size(), is(1));

      tsa.attachStripe(server("server-3", "localhost")
          .tsaPort(9610)
          .tsaGroupPort(9611)
          .configRepo("terracotta3/repository")
          .logs("terracotta3/logs")
          .metaData("terracotta3/metadata"));

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(3));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(1).size(), is(1));
    }
  }

  @Test
  public void testSingleStripeFormation() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );


    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testSingleStripeFormation", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll().attachAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(2));
    }
  }

  @Test
  public void testMultiStripeFormation() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        ),
                        stripe(
                            server("server-3", "localhost")
                                .tsaPort(9610)
                                .tsaGroupPort(9611)
                                .configRepo("terracotta3/repository")
                                .logs("terracotta3/logs")
                                .metaData("terracotta3/metadata"),
                            server("server-4", "localhost")
                                .tsaPort(9710)
                                .tsaGroupPort(9711)
                                .configRepo("terracotta4/repository")
                                .logs("terracotta4/logs")
                                .metaData("terracotta4/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testMultiStripeFormation", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.attachAll();

      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(1).size(), is(2));
    }
  }

  @Test
  public void testDynamicNodeDetach() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testDynamicNodeDetach", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getDiagnosticModeSevers().size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(2));

      tsa.detachNode(0, 1);

      assertThat(tsa.getDiagnosticModeSevers().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));
    }
  }

  @Test
  public void testDynamicStripeDetach() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata")
                        ),
                        stripe(
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testDynamicStripeDetach", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();

      assertThat(tsa.getDiagnosticModeSevers().size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(2));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(1).size(), is(1));

      tsa.detachStripe(1);

      assertThat(tsa.getDiagnosticModeSevers().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().size(), is(1));
      assertThat(tsa.getTsaConfigurationContext().getTopology().getStripes().get(0).size(), is(1));
    }
  }

  @Test
  public void testNodeActivation() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testNodeActivation", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.startAll().attachAll().activateAll();

      assertThat(tsa.getDiagnosticModeSevers().size(), is(0));
      assertThat(tsa.getActives().size(), is(1));
      assertThat(tsa.getPassives().size(), is(1));
    }
  }

  @Test
  public void testIpv6() throws Exception {
    CustomConfigurationContext context = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA),
                    dynamicCluster(
                        stripe(
                            server("node-1", "::1")
                                .bindAddress("::")
                                .groupBindAddress("::")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .logs("terracotta1/logs")
                                .configRepo("terracotta1/repo")
                                .metaData("terracotta1/metadata"),
                            server("node-2", "::1")
                                .bindAddress("::")
                                .groupBindAddress("::")
                                .tsaPort(9510)
                                .tsaGroupPort(9511)
                                .logs("terracotta2/logs")
                                .configRepo("terracotta2/repo")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );
    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testIpv6", context)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      assertThat(tsa.getDiagnosticModeSevers().size(), is(2));

      tsa.attachAll();
      tsa.activateAll();
      assertThat(tsa.getActives().size(), is(1));
      assertThat(tsa.getPassives().size(), is(1));
    }
  }
}
