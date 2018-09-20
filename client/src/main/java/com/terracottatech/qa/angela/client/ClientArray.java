package com.terracottatech.qa.angela.client;

import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.clientconfig.TerracottaClient;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.ClientTopology;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.HardwareStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.terracottatech.qa.angela.client.ClusterFactory.DEFAULT_ALLOWED_JDK_VENDORS;
import static com.terracottatech.qa.angela.client.ClusterFactory.DEFAULT_JDK_VERSION;

/**
 * @author Aurelien Broszniowski
 */

public class ClientArray implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(ClientArray.class);

  private final Ignite ignite;
  private final Supplier<InstanceId> instanceIdSupplier;
  private final ClientTopology topology;
  private final License license;
  private boolean closed = false;
  private LocalKitManager localKitManager;
  private Map<TerracottaClient, Client> clients = new HashMap<>();
  private HardwareStats hardwareStats;

  public ClientArray(Ignite ignite, Supplier<InstanceId> instanceIdSupplier, ClientTopology topology, License license) {
    if (!topology.getLicenseType().isOpenSource() && license == null) {
      throw new IllegalArgumentException("LicenseType " + topology.getLicenseType() + " requires a license.");
    }
    this.topology = topology;
    this.license = license;
    this.instanceIdSupplier = instanceIdSupplier;
    this.ignite = ignite;
    this.localKitManager = new LocalKitManager(topology.getDistribution());
    this.hardwareStats = new HardwareStats();
    installAll();
  }

  private void installAll() {
    final Collection<TerracottaClient> terracottaClients = topology.getClients();
    for (TerracottaClient terracottaClient : terracottaClients) {
      install(terracottaClient);
    }
  }

  private void install(final TerracottaClient terracottaClient) {
    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));

    logger.info("Setting up locally the extracted install to be deployed remotely");
    String kitInstallationPath = System.getProperty("kitInstallationPath");
    localKitManager.setupLocalInstall(license, kitInstallationPath, offline);

    try {
      logger.info("installing the client jars to {}", terracottaClient.getHostname());
// TODO : refactor Client to extract the uploading step and the execution step
//     uploadClientJars(ignite, terracottaClient.getHostname(), instanceId, );

      Client client = new Client(ignite, instanceIdSupplier.get(), terracottaClient.getHostname(),
          new TerracottaCommandLineEnvironment(DEFAULT_JDK_VERSION, DEFAULT_ALLOWED_JDK_VENDORS, null), localKitManager);
      clients.put(terracottaClient, client);

    } catch (Exception e) {
      throw new RuntimeException("Cannot upload client jars to " + terracottaClient.getHostname(), e);
    }
  }

  private void uninstallAll() throws IOException {
    final Collection<TerracottaClient> terracottaClients = topology.getClients();
    for (TerracottaClient terracottaClient : terracottaClients) {
      uninstall(terracottaClient);
    }
  }

  private void uninstall(final TerracottaClient terracottaClient) throws IOException {
    logger.info("uninstalling from {}", terracottaClient.getHostname());
    final Client client = clients.get(terracottaClient);
    if (client != null) { client.close();}
    clients.remove(terracottaClient);
  }

  public List<Future<Void>> executeAll(final ClientJob clientJob) {
    List<Future<Void>> futures = new ArrayList<>();
    final Collection<TerracottaClient> terracottaClients = topology.getClients();
    for (TerracottaClient terracottaClient : terracottaClients) {
      futures.add(execute(terracottaClient, clientJob));
    }
    return futures;
  }

  public Future<Void> execute(final TerracottaClient terracottaClient, final ClientJob clientJob) {
    final Future<Void> submit = clients.get(terracottaClient).submit(clientJob);
    return submit;
  }

  @Override
  public void close() throws Exception {
    if (closed) {
      return;
    }
    closed = true;

    if (!ClusterFactory.SKIP_UNINSTALL) {
      uninstallAll();
    }
  }

  public List<Client> getClients() {
    return new ArrayList<>(this.clients.values());
  }
}
