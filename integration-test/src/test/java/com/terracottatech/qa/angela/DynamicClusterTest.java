package com.terracottatech.qa.angela;

import com.terracotta.connection.api.DiagnosticConnectionService;
import com.terracotta.diagnostic.Diagnostics;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ConfigTool;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.net.ClientToServerDisruptor;
import com.terracottatech.qa.angela.client.net.ServerToServerDisruptor;
import com.terracottatech.qa.angela.client.net.SplitCluster;
import com.terracottatech.qa.angela.common.ConfigToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.StoreOperationAbandonedException;
import com.terracottatech.store.Type;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import org.apache.commons.lang3.ArrayUtils;
import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.Test;
import org.terracotta.connection.Connection;
import org.terracotta.connection.ConnectionService;
import org.terracotta.connection.entity.EntityRef;

import java.net.URI;
import java.util.Collection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.tc.util.Assert.assertEquals;
import static com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext.customConfigurationContext;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.dynamic_cluster.Stripe.stripe;
import static com.terracottatech.qa.angela.common.provider.DynamicConfigManager.dynamicCluster;
import static com.terracottatech.qa.angela.common.tcconfig.TerracottaServer.server;
import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

@org.junit.Ignore("TDB-4771: Ignore until dynamic-config kits are published to Kratos")
public class DynamicClusterTest {
  private static final int STATE_TIMEOUT = 60_000;
  private static final int STATE_POLL_INTERVAL = 1_000;

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

  @Test
  public void testClientToServerDisruption() throws Exception {
    CellDefinition<Integer> CELL_1 = CellDefinition.defineInt("cell1");
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA), true,
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
    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testClientToServerDisruption", configContext)) {
      try (Tsa tsa = factory.tsa().startAll().activateAll()) {
        Map<ServerSymbolicName, Integer> proxyTsaPorts = tsa.updateToProxiedPorts();
        TerracottaServer terracottaServer = tsa.getActive();
        int proxyPort = proxyTsaPorts.get(terracottaServer.getServerSymbolicName());
        ConfigToolExecutionResult result = tsa.configTool(terracottaServer).executeCommand("set", "-s", "localhost:" + terracottaServer.getTsaPort(),
            "-c", "stripe.1.node.1.node-public-hostname=localhost", "-c", "stripe.1.node.1.node-public-port=" + proxyPort);
        assertTrue(result.getOutput().contains("Command successful!"));
        try (ClientToServerDisruptor disruptor = tsa.disruptionController().newClientToServerDisruptor()) {
          URI uri = disruptor.uri();
          try (DatasetManager datasetManager = DatasetManager.clustered(uri)
              .withConnectionTimeout(150, TimeUnit.SECONDS)
              .withReconnectTimeout(300, TimeUnit.SECONDS)
              .build()) {
            System.out.println("before newDataset");
            Assert.assertTrue(datasetManager.newDataset("store-0", Type.INT, datasetManager.datasetConfiguration()
                .offheap("main").build()));
            Dataset<Integer> dataset = datasetManager.getDataset("store-0", Type.INT);
            DatasetWriterReader<Integer> writerReader = dataset.writerReader();
            Assert.assertTrue(writerReader.add(1, CELL_1.newCell(1)));

            //partition client and server and stop it before reconnect timeout
            disruptor.disrupt();
            CompletableFuture.runAsync(() -> {
              try {
                Thread.sleep(155_000);
              } catch (InterruptedException e) {
              }
              disruptor.undisrupt();
            });

            //store operation during partition and verify it first throws StoreOperationAbandonedException after
            //network restored and then it can resume upon resubmit.
            try {
              writerReader.add(2, CELL_1.newCell(2));
              Assert.fail("expected StoreOperationAbandonedException not thrown");
            } catch (StoreOperationAbandonedException e) {
              //this exception expected as reconnect happened during this operation.
              writerReader.add(2, CELL_1.newCell(2));
            }
          }
        }
      }
    }
  }

  @Test
  public void testServerToServerOneActiveOnePassiveDisruption() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA), true,
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .failoverPriority("consistency:1")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9760)
                                .tsaGroupPort(9711)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .failoverPriority("consistency:1")
                                .metaData("terracotta2/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );
    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testServerToServerDisruption", configContext)) {
      try (Tsa tsa = factory.tsa().startAll().attachAll().activateAll()) {

        TerracottaServer active = tsa.getActive();
        TerracottaServer passive = tsa.getPassive();

        doPreProcessingForOneActiveOnePassiveDisruption(tsa, active, passive);

        active = tsa.getActive();
        passive = tsa.getPassive();
        SplitCluster split1 = new SplitCluster(active);
        SplitCluster split2 = new SplitCluster(passive);

        //server to server disruption with active at one end and passives at other end.
        try (ServerToServerDisruptor disruptor = tsa.disruptionController()
            .newServerToServerDisruptor(split1, split2)) {

          //start partition
          disruptor.disrupt();
          //verify active and passive gets blocked
          Assert.assertTrue(waitForServerBlocked(active));
          Assert.assertTrue(waitForServerBlocked(passive));

          //stop partition
          disruptor.undisrupt();

          Assert.assertTrue(isActive(tsa, active));
        }
      }
    }
  }

  @Test
  public void testServerToServerOneActiveMultiplePassiveDisruption() throws Exception {
    ConfigurationContext configContext = customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version("10.7.0-SNAPSHOT"), KIT, TERRACOTTA), true,
                    dynamicCluster(
                        stripe(
                            server("server-1", "localhost")
                                .tsaPort(9410)
                                .tsaGroupPort(9411)
                                .configRepo("terracotta1/repository")
                                .logs("terracotta1/logs")
                                .failoverPriority("consistency")
                                .metaData("terracotta1/metadata"),
                            server("server-2", "localhost")
                                .tsaPort(9760)
                                .tsaGroupPort(9761)
                                .configRepo("terracotta2/repository")
                                .logs("terracotta2/logs")
                                .failoverPriority("consistency")
                                .metaData("terracotta2/metadata"),
                            server("server-3", "localhost")
                                .tsaPort(9780)
                                .tsaGroupPort(9781)
                                .configRepo("terracotta3/repository")
                                .logs("terracotta3/logs")
                                .failoverPriority("consistency")
                                .metaData("terracotta3/metadata")
                        )
                    )
                )
            )
            .license(TERRACOTTA.defaultLicense())
        );
    try (ClusterFactory factory = new ClusterFactory("DynamicClusterTest::testServerToServerMultiplePassiveDisruption", configContext)) {
      try (Tsa tsa = factory.tsa().startAll().attachAll().activateAll()) {

        TerracottaServer active = tsa.getActive();
        Collection<TerracottaServer> passives = tsa.getPassives();
        Iterator<TerracottaServer> iterator = passives.iterator();
        TerracottaServer passive1 = iterator.next();
        TerracottaServer passive2 = iterator.next();

        doPreProcessingForOneActiveMultiplePassiveDisruption(tsa, active, passive1, passive2);

        active = tsa.getActive();
        passives = tsa.getPassives();
        iterator = passives.iterator();
        passive1 = iterator.next();
        passive2 = iterator.next();

        SplitCluster split1 = new SplitCluster(active);
        SplitCluster split2 = new SplitCluster(passives);

        //server to server disruption with active at one end and passives at other end.
        try (ServerToServerDisruptor disruptor = tsa.disruptionController()
            .newServerToServerDisruptor(split1, split2)) {

          //start partition
          disruptor.disrupt();
          //verify active gets into blocked state and one of passives gets promoted to active
          Assert.assertTrue(waitForServerBlocked(active));
          Assert.assertTrue(isActive(tsa, passive1, passive2));


          //stop partition
          disruptor.undisrupt();

          //verify former active gets zapped and becomes passive after network restored
          Assert.assertTrue(isPassive(tsa, active));

        }
      }
    }
  }

  private Connection openDiagnosticConnection(TerracottaServer server) throws TimeoutException {
    return await().atMost(STATE_TIMEOUT, TimeUnit.MILLISECONDS).pollInterval(STATE_POLL_INTERVAL, TimeUnit.MILLISECONDS)
        .until(() -> tryDiagnosticConnection(server), IsNull.notNullValue());
  }

  private Diagnostics FetchDiagnosticEntity(Connection connection) throws Exception {
    EntityRef<Diagnostics, Object, Void> ref = connection.getEntityRef(Diagnostics.class, 1, "root");
    return ref.fetchEntity(null);
  }

  private Connection tryDiagnosticConnection(TerracottaServer server) {
    try {
      ConnectionService connectionService = new DiagnosticConnectionService();
      URI uri = URI.create("diagnostic://" + server.getHostname() + ":" + server.getTsaPort());
      return connectionService.connect(uri, new Properties());
    } catch (Exception e) {
      //TODO logger
      e.printStackTrace();
      return null;
    }
  }

  private boolean isServerBlocked(TerracottaServer server) throws Exception {
    try (Connection connection = openDiagnosticConnection(server)) {
      try (Diagnostics diagnostics = FetchDiagnosticEntity(connection)) {
        return Boolean.valueOf(diagnostics.invoke("ConsistencyManager", "isBlocked"));
      }
    }
  }

  private boolean waitForServerBlocked(TerracottaServer server) throws Exception {
    await().atMost(STATE_TIMEOUT, TimeUnit.MILLISECONDS)
        .pollInterval(STATE_POLL_INTERVAL, TimeUnit.MILLISECONDS)
        .until(() -> isServerBlocked(server));
    return true;
  }

  private boolean isActive(Tsa tsa, TerracottaServer... servers) throws Exception {
    await().atMost(STATE_TIMEOUT, TimeUnit.MILLISECONDS)
        .pollInterval(STATE_POLL_INTERVAL, TimeUnit.MILLISECONDS)
        .until(() -> Arrays.asList(servers).stream().anyMatch(server -> tsa.getState(server) == STARTED_AS_ACTIVE));
    TerracottaServer active = Arrays.asList(servers)
        .stream()
        .filter(server -> tsa.getState(server) == STARTED_AS_ACTIVE)
        .findFirst()
        .get();
    TerracottaServer[] passives = ArrayUtils.removeElements(servers, active);
    return passives.length == 0 || isPassive(tsa, passives);
  }

  private boolean isPassive(Tsa tsa, TerracottaServer... servers) {
    await().atMost(STATE_TIMEOUT, TimeUnit.MILLISECONDS)
        .pollInterval(STATE_POLL_INTERVAL, TimeUnit.MILLISECONDS)
        .until(() -> Arrays.asList(servers).stream().allMatch(server -> tsa.getState(server) == STARTED_AS_PASSIVE));
    return true;
  }

  private void doPreProcessingForOneActiveOnePassiveDisruption(Tsa tsa, TerracottaServer active, TerracottaServer passive) throws Exception {
    ConfigTool configTool = tsa.configTool(active);

    ConfigToolExecutionResult executionResult = configTool.executeCommand(
        "export", "-s", active.getHostname() + ":" + active.getTsaPort(), "-x");
    int activeNumber = 0, passiveNumber = 0; // which one is 1 and 2 in ordering.
    for (String row : executionResult.getOutput()) {
      if (row.contains(String.valueOf(active.getTsaGroupPort()))) {
        if (row.contains("2")) {
          activeNumber = 2;
          passiveNumber = 1;
        } else {
          activeNumber = 1;
          passiveNumber = 2;
        }
        break;
      }
    }

    //Change group port of passive on active
    Map<String, Integer> proxyGroupPortMapping = tsa.getProxyGroupPortsForServer(active);
    assertEquals(proxyGroupPortMapping.size(), 1);
    String passiveServerName = passive.getServerSymbolicName().getSymbolicName();
    String setting = "stripe.1.node." + activeNumber + ".tc-properties.com.terracottatech.group-port.simulate=" +
        passiveServerName + "#" + proxyGroupPortMapping.get(passiveServerName);
    executionResult = configTool.executeCommand(
        "set", "-s", active.getHostname() + ":" + active.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    //Change group port of active on passive
    proxyGroupPortMapping = tsa.getProxyGroupPortsForServer(passive);
    assertEquals(proxyGroupPortMapping.size(), 1);
    String activeServerName = active.getServerSymbolicName().getSymbolicName();
    setting = "stripe.1.node." + passiveNumber + ".tc-properties.com.terracottatech.group-port.simulate=" +
        activeServerName + "#" + proxyGroupPortMapping.get(activeServerName);
    executionResult = configTool.executeCommand(
        "set", "-s", passive.getHostname() + ":" + passive.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    //Do restart all servers with updated group ports in each node
    Topology topology = tsa.getTsaConfigurationContext().getTopology();
    TerracottaServer terracottaServer1 = topology.getServer(active.getServerSymbolicName());
    TerracottaServer terracottaServer2 = topology.getServer(passive.getServerSymbolicName());

    tsa.stopAll();
    assertThat(tsa.getState(terracottaServer1), is(TerracottaServerState.STOPPED));
    assertThat(tsa.getState(terracottaServer2), is(TerracottaServerState.STOPPED));

    Thread t = new Thread(() -> {
      tsa.start(terracottaServer2, "-r", terracottaServer2.getConfigRepo());
    });
    t.start();
    tsa.start(terracottaServer1, "-r", terracottaServer1.getConfigRepo());
    t.join();
  }

  private void doPreProcessingForOneActiveMultiplePassiveDisruption(Tsa tsa, TerracottaServer active, TerracottaServer passive1, TerracottaServer passive2) throws Exception {
    ConfigTool configTool = tsa.configTool(active);

    ConfigToolExecutionResult executionResult = configTool.executeCommand(
        "export", "-s", active.getHostname() + ":" + active.getTsaPort(), "-x");
    int activeNumber = 0, passive1Number = 0, passive2Number = 0; // which one is 1 and 2 and 3 in ordering.

    for (String row : executionResult.getOutput()) {
      if (activeNumber == 0 || passive1Number == 0 || passive2Number == 0) {
        if (row.contains(String.valueOf(active.getTsaGroupPort()))) {
          if (row.contains("3")) {
            activeNumber = 3;
          } else if (row.contains("2")) {
            activeNumber = 2;
          } else {
            activeNumber = 1;
          }
        }
        if (row.contains(String.valueOf(passive1.getTsaGroupPort()))) {
          if (row.contains("3")) {
            passive1Number = 3;
          } else if (row.contains("2")) {
            passive1Number = 2;
          } else {
            passive1Number = 1;
          }
        }
        if (row.contains(String.valueOf(passive2.getTsaGroupPort()))) {
          if (row.contains("3")) {
            passive2Number = 3;
          } else if (row.contains("2")) {
            passive2Number = 2;
          } else {
            passive2Number = 1;
          }
        }
      } else {
        break;
      }
    }

    //Change group port of passives on active
    Map<String, Integer> proxyGroupPortMapping = tsa.getProxyGroupPortsForServer(active);
    assertEquals(proxyGroupPortMapping.size(), 2);
    String passiveServerName = passive1.getServerSymbolicName().getSymbolicName();
    String setting = "stripe.1.node." + activeNumber + ".tc-properties.com.terracottatech.group-port.simulate=" +
        passiveServerName + "#" + proxyGroupPortMapping.get(passiveServerName);
    executionResult = configTool.executeCommand(
        "set", "-s", active.getHostname() + ":" + active.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    passiveServerName = passive2.getServerSymbolicName().getSymbolicName();
    setting = "stripe.1.node." + activeNumber + ".tc-properties.com.terracottatech.group-port.simulate=" +
        passiveServerName + "#" + proxyGroupPortMapping.get(passiveServerName);
    executionResult = configTool.executeCommand(
        "set", "-s", active.getHostname() + ":" + active.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));


    //Change group port of active and passive2 on passive1
    proxyGroupPortMapping = tsa.getProxyGroupPortsForServer(passive1);
    assertEquals(proxyGroupPortMapping.size(), 2);
    String activeServerName = active.getServerSymbolicName().getSymbolicName();
    setting = "stripe.1.node." + passive1Number + ".tc-properties.com.terracottatech.group-port.simulate=" +
        activeServerName + "#" + proxyGroupPortMapping.get(activeServerName);
    executionResult = configTool.executeCommand(
        "set", "-s", passive1.getHostname() + ":" + passive1.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    String otherPassiveName = passive2.getServerSymbolicName().getSymbolicName();
    setting = "stripe.1.node." + passive1Number + ".tc-properties.com.terracottatech.group-port.simulate=" +
        otherPassiveName + "#" + proxyGroupPortMapping.get(otherPassiveName);
    executionResult = configTool.executeCommand(
        "set", "-s", passive1.getHostname() + ":" + passive1.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    //Change group port of active and passive1 on passive2
    proxyGroupPortMapping = tsa.getProxyGroupPortsForServer(passive2);
    assertEquals(proxyGroupPortMapping.size(), 2);
    activeServerName = active.getServerSymbolicName().getSymbolicName();
    setting = "stripe.1.node." + passive2Number + ".tc-properties.com.terracottatech.group-port.simulate=" +
        activeServerName + "#" + proxyGroupPortMapping.get(activeServerName);
    executionResult = configTool.executeCommand(
        "set", "-s", passive2.getHostname() + ":" + passive2.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    otherPassiveName = passive1.getServerSymbolicName().getSymbolicName();
    setting = "stripe.1.node." + passive2Number + ".tc-properties.com.terracottatech.group-port.simulate=" +
        otherPassiveName + "#" + proxyGroupPortMapping.get(otherPassiveName);
    executionResult = configTool.executeCommand(
        "set", "-s", passive2.getHostname() + ":" + passive2.getTsaPort(),
        "-c", setting);
    assertThat(executionResult.getExitStatus(), equalTo(0));

    //Do restart all servers with updated group ports in each node
    Topology topology = tsa.getTsaConfigurationContext().getTopology();
    TerracottaServer terracottaServer1 = topology.getServer(active.getServerSymbolicName());
    TerracottaServer terracottaServer2 = topology.getServer(passive1.getServerSymbolicName());
    TerracottaServer terracottaServer3 = topology.getServer(passive2.getServerSymbolicName());

    tsa.stopAll();
    assertThat(tsa.getState(terracottaServer1), is(TerracottaServerState.STOPPED));
    assertThat(tsa.getState(terracottaServer2), is(TerracottaServerState.STOPPED));
    assertThat(tsa.getState(terracottaServer3), is(TerracottaServerState.STOPPED));

    Thread t1 = new Thread(() -> {
      tsa.start(terracottaServer2, "-r", terracottaServer2.getConfigRepo());
    });
    t1.start();
    Thread t2 = new Thread(() -> {
      tsa.start(terracottaServer3, "-r", terracottaServer3.getConfigRepo());
    });
    t2.start();
    tsa.start(terracottaServer1, "-r", terracottaServer1.getConfigRepo());
    t1.join();
    t2.join();
  }
}
