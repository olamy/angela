package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.config.ClientArrayConfigurationContext;
import com.terracottatech.qa.angela.common.clientconfig.ClientId;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


/**
 * @author Aurelien Broszniowski
 */
public class ClientArray implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(ClientArray.class);

  private final Ignite ignite;
  private final Supplier<InstanceId> instanceIdSupplier;
  private final LocalKitManager localKitManager;
  private final Map<ClientId, Client> clients = new HashMap<>();
  private final ClientArrayConfigurationContext clientArrayConfigurationContext;
  private boolean closed = false;

  ClientArray(Ignite ignite, Supplier<InstanceId> instanceIdSupplier, ClientArrayConfigurationContext clientArrayConfigurationContext) {
    this.clientArrayConfigurationContext = clientArrayConfigurationContext;
    this.instanceIdSupplier = instanceIdSupplier;
    this.ignite = ignite;
    this.localKitManager = new LocalKitManager(clientArrayConfigurationContext.getClientArrayTopology().getDistribution());
    installAll();
  }

  private void installAll() {
    for (ClientId clientId : clientArrayConfigurationContext.getClientArrayTopology().getClientIds()) {
      install(clientId);
    }
  }

  private void install(ClientId clientId) {
    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));

    logger.info("Setting up locally the extracted install to be deployed remotely");
    String kitInstallationPath = System.getProperty("kitInstallationPath");
    localKitManager.setupLocalInstall(clientArrayConfigurationContext.getLicense(), kitInstallationPath, offline);

    try {
      logger.info("installing the client jars to {}", clientId);
// TODO : refactor Client to extract the uploading step and the execution step
//     uploadClientJars(ignite, terracottaClient.getClientHostname(), instanceId, );

      Client client = new Client(ignite, instanceIdSupplier.get(), clientId.getClientHostname().getHostname(), clientArrayConfigurationContext.getTerracottaCommandLineEnvironment(), localKitManager);
      clients.put(clientId, client);
    } catch (Exception e) {
      throw new RuntimeException("Cannot upload client jars to " + clientId, e);
    }
  }

  private void uninstallAll() throws IOException {
    List<Exception> exceptions = new ArrayList<>();

    for (ClientId clientId : clientArrayConfigurationContext.getClientArrayTopology().getClientIds()) {
      try {
        uninstall(clientId);
      } catch (Exception ioe) {
        exceptions.add(ioe);
      }
    }

    if (!exceptions.isEmpty()) {
      IOException ioException = new IOException("Error uninstalling some clients");
      exceptions.forEach(ioException::addSuppressed);
      throw ioException;
    }
  }

  private void uninstall(ClientId clientId) throws IOException {
    logger.info("uninstalling from {}", clientId);
    Client client = clients.get(clientId);
    try {
      if (client != null) {
        client.close();
      }
    } finally {
      clients.remove(clientId);
    }
  }

  public ClientArrayConfigurationContext getClientArrayConfigurationContext() {
    return clientArrayConfigurationContext;
  }

  public ClientArrayFuture executeOnAll(ClientJob clientJob) {
    List<Future<Void>> futures = new ArrayList<>();
    for (ClientId clientId : clientArrayConfigurationContext.getClientArrayTopology().getClientIds()) {
      for (int i = 0; i < clientId.getClientHostname().getHostsCount().get(); i++) {
        futures.add(executeOn(clientId, clientJob));
      }
    }
    return new ClientArrayFuture(futures);
  }

  public Future<Void> executeOn(ClientId clientId, ClientJob clientJob) {
    return clients.get(clientId).submit(clientJob);
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

  public Collection<Client> getClients() {
    return Collections.unmodifiableCollection(this.clients.values());
  }

}
