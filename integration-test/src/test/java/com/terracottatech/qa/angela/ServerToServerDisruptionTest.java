package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.terracotta.connection.Connection;
import org.terracotta.connection.ConnectionService;
import org.terracotta.connection.entity.EntityRef;

import com.terracotta.connection.api.DiagnosticConnectionService;
import com.terracotta.diagnostic.Diagnostics;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.net.ServerToServerDisruptor;
import com.terracottatech.qa.angela.client.net.SplitCluster;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_4X_AP;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ServerToServerDisruptionTest {
  private static final int STATE_TIMEOUT = 60_000;
  private static final int STATE_POLL_INTERVAL = 1_000;

  /**
   * Create partition between [active] & [passive1,passive2] in consistent mode and verify
   * state of servers.
   */
  @Ignore("TDB-4769")
  @Test
  public void testPartitionBetweenActivePassives() throws Exception {
    //set netDisruptionEnabled to true to enable disruption
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), true,
            tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-app-consistent.xml"))))
            .license(LicenseType.TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("TcDBTest::testConnection", configContext)) {
      try (Tsa tsa = factory.tsa().startAll().licenseAll()) {
        TerracottaServer active = tsa.getActive();
        Collection<TerracottaServer> passives = tsa.getPassives();
        Iterator<TerracottaServer> iterator = passives.iterator();
        TerracottaServer passive1 = iterator.next();
        TerracottaServer passive2 = iterator.next();


        SplitCluster split1 = new SplitCluster(active);
        SplitCluster split2 = new SplitCluster(passives);

        //server to server disruption with active at one end and passives at other end.
        try (ServerToServerDisruptor disruptor = tsa.disruptionController()
            .newServerToServerDisruptor(split1, split2)) {

          //start partition
          disruptor.disrupt();
          //verify active gets into blocked state and one of passives gets promoted to active
          Assert.assertTrue(waitForServerBlocked(active));
          Assert.assertTrue(waitForActive(tsa, passive1, passive2));


          //stop partition
          disruptor.undisrupt();
          //verify former active gets zapped and becomes passive after network restored
          Assert.assertTrue(waitForPassive(tsa, active));

        }
      }

    }
  }

  private static String getServerBlockedState(TerracottaServer server) throws Exception {
    ConnectionService connectionService = new DiagnosticConnectionService();
    URI uri = URI.create("diagnostic://" + server.getHostname() + ":" + server.getTsaPort());
    try (Connection connection = connectionService.connect(uri, new Properties())) {
      EntityRef<Diagnostics, Object, Void> ref = connection.getEntityRef(Diagnostics.class, 1, "root");
      try (Diagnostics diagnostics = ref.fetchEntity(null)) {
        return diagnostics.invoke("ConsistencyManager", "isBlocked");
      }
    }
  }

  private static boolean isServerBlocked(TerracottaServer server) {
    try {
      return Boolean.parseBoolean(getServerBlockedState(server));
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean waitForServerBlocked(TerracottaServer server) throws Exception {
    long endTime = System.currentTimeMillis() + STATE_TIMEOUT;
    while (endTime > System.currentTimeMillis()) {
      if (isServerBlocked(server)) {
        return true;
      } else {
        Thread.sleep(STATE_POLL_INTERVAL);
      }
    }
    throw new TimeoutException("Timeout when waiting for server to become blocked");
  }

  private static boolean waitForActive(Tsa tsa, TerracottaServer... servers) throws Exception {
    long endTime = System.currentTimeMillis() + STATE_TIMEOUT;
    int activeIndex = -1;
    while (endTime > System.currentTimeMillis()) {
      //first make sure one of server becoming active and then check remaining servers for passive state
      if (activeIndex == -1) {
        for (int i = 0; i < servers.length; ++i) {
          if (tsa.getState(servers[i]) == STARTED_AS_ACTIVE) {
            activeIndex = i;
            break;
          }
        }
      }
      if (activeIndex == -1) {
        Thread.sleep(STATE_POLL_INTERVAL);
      } else {
        TerracottaServer[] passives = ArrayUtils.remove(servers, activeIndex);
        return passives.length == 0 || waitForPassive(tsa, passives);
      }
    }
    throw new TimeoutException("Timeout when waiting for server to become active");
  }

  private static boolean waitForPassive(Tsa tsa, TerracottaServer... servers) throws Exception {
    long endTime = System.currentTimeMillis() + STATE_TIMEOUT;
    while (endTime > System.currentTimeMillis()) {
      for (int i = 0; i < servers.length; ++i) {
        if (tsa.getState(servers[i]) != STARTED_AS_PASSIVE) {
          break;
        }
        return true;
      }
      Thread.sleep(STATE_POLL_INTERVAL);
    }
    throw new TimeoutException("Timeout when waiting for server to become passive");
  }


  @Test
  public void testFailoverActivePassiveStripe() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
                tcConfig(version(Versions.TERRACOTTA_VERSION_4X), TC_CONFIG_4X_AP)))
            .license(LicenseType.MAX.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("ServerToServerDisruptionTest::testFailoverActivePassiveStripe", config)) {
      Tsa tsa = factory.tsa();
      tsa.startAll();
      tsa.licenseAll();

      assertThat(new ArrayList<>(tsa.getStarted()).size(), is(2));

      assertThat(tsa.getActive(), is(not(nullValue())));
      assertThat(tsa.getPassive(), is(not(nullValue())));

      final TerracottaServer active = tsa.getActive();
      tsa.stop(active);

      assertThat(new ArrayList<>(tsa.getStarted()).size(), is(1));

      await().atMost(25, SECONDS).until(tsa::getPassive, is(nullValue()));
      assertThat(tsa.getActive(), is(not(nullValue())));

      tsa.start(active);

      await().atMost(25, SECONDS).until(tsa::getPassive, is(not(nullValue())));

      assertThat(tsa.getStarted().size(), equalTo(2));
    }
  }
}
