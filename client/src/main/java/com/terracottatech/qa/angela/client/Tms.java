package com.terracottatech.qa.angela.client;

import io.restassured.path.json.JsonPath;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.http.HttpUtils;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.FileMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;

public class Tms implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Tsa.class);
  private static final long TIMEOUT = 60000;
  private static final String API_CONNECTIONS_PROBE_OLD = "/api/connections/probe/";
  private static final String API_CONNECTIONS_PROBE_NEW = "/api/connections/probe?uri=";
  private final String tmsHostname;
//  private final String kitInstallationPath;
  private final Distribution distribution;
  private final TerracottaCommandLineEnvironment tcEnv;
  private boolean closed = false;
  private final Ignite ignite;
  private final License license;
  private final InstanceId instanceId;
  private final TmsServerSecurityConfig securityConfig;
  private LocalKitManager localKitManager;


  public Tms(Ignite ignite, InstanceId instanceId, License license, String hostname, Distribution distribution, TmsServerSecurityConfig securityConfig, TerracottaCommandLineEnvironment tcEnv) {
    this.distribution = distribution;
    this.tcEnv = tcEnv;
    if (license == null) {
      throw new IllegalArgumentException("TMS requires a license.");
    }
    this.license = license;
    this.instanceId = instanceId;
    this.ignite = ignite;
    this.tmsHostname = hostname;
    this.securityConfig = securityConfig;
    this.localKitManager = new LocalKitManager(distribution);

  }

  /**
   *
   * This method connects to the TMS via HTTP (insecurely) REST calls.  It also creates a TMS connection to the cluster.
   *
   * @param uri of the cluster to connect to
   * @return connectionName
   */
  public String connectToCluster(URI uri) {
    return connectToCluster(uri, null);
  }

  /**
   *
   * This method connects to the TMS via HTTPS (securely) REST calls.  It also creates a TMS connection to the cluster.
   * If cluster security is enabled it will connect to the cluster via SSL/TLS, otherwise if connects via plain text.
   *
   * @param uri of the cluster to connect to
   * @param tmsClientSecurityConfig
   * @return connectionName
   */
  public String connectToCluster(URI uri, TmsClientSecurityConfig tmsClientSecurityConfig) {
    String connectionName;
    logger.info("connecting TMS to {}", uri.toString());
    // probe
    String url;
    String response;
    try {
      response = probeOldStyle(uri, tmsClientSecurityConfig);
    } catch (HttpUtils.FailedHttpRequestException e) {
      // TDB-3370 / https://irepo.eur.ad.sag/projects/TAB/repos/terracotta-enterprise/pull-requests/1580/overview
      // in 10.3, probe calls need to use /probe?uri=host:port instead of : /probe/host:port in 10.2
      response = probeNewStyle(uri, tmsClientSecurityConfig);
    }

    // create connection
    url = (tmsClientSecurityConfig != null ? "https://" : "http://") + tmsHostname + ":9480/api/connections";

    response = HttpUtils.sendPostRequest(url, response, tmsClientSecurityConfig);
    logger.info("tms connect result :" + response);

    connectionName = JsonPath.from(response).get("config.connectionName");

    return connectionName;
  }


  private String probeOldStyle(URI uri, TmsClientSecurityConfig securityConfig) {
    return probe(uri, API_CONNECTIONS_PROBE_OLD, securityConfig);
  }

  private String probeNewStyle(URI uri, TmsClientSecurityConfig securityConfig) {
    return probe(uri, API_CONNECTIONS_PROBE_NEW, securityConfig);
  }

  private String probe(URI uri, String probeEndpoint, TmsClientSecurityConfig securityConfig) {
    String url;
    try {
      url = (securityConfig != null ? "https://" : "http://")   + tmsHostname + ":9480" + probeEndpoint +
          URLEncoder.encode(uri.toString(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Could not encode terracotta connection url", e);
    }
    String response = HttpUtils.sendGetRequest(url, securityConfig);
    logger.info("tms probe result :" + response);
    return response;
  }

  public RemoteFolder browse(String root) {
    String path = executeRemotely(tmsHostname, () -> Agent.CONTROLLER.getTmsInstallationPath(instanceId));
    return new RemoteFolder(ignite, tmsHostname, path, root);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    try {
      stop();
    } catch (Exception e) {
      // ignore, not installed
    }
    if (!ClusterFactory.SKIP_UNINSTALL) {
      uninstall();
    }
  }

  private void uninstall() {
    TerracottaManagementServerState terracottaServerState = getTmsState(tmsHostname);
    if (terracottaServerState == null) {
      return;
    }
    if (terracottaServerState != TerracottaManagementServerState.STOPPED) {
      throw new IllegalStateException("Cannot uninstall: server " + tmsHostname + " already in state " + terracottaServerState);
    }

    logger.info("uninstalling from {}", tmsHostname);
    executeRemotely(tmsHostname, TIMEOUT, () -> Agent.CONTROLLER.uninstallTms(instanceId, distribution, localKitManager.getKitInstallationName(), tmsHostname));
  }

  private void stop() {
    stopTms(tmsHostname);
  }

  private void executeRemotely(final String hostname, final long timeout, final IgniteRunnable runnable) {
    IgniteHelper.checkAgentHealth(ignite, hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).withTimeout(timeout).broadcast(runnable);
  }

  private <R> R executeRemotely(final String hostname, final IgniteCallable<R> callable) {
    IgniteHelper.checkAgentHealth(ignite, hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    Collection<R> results = ignite.compute(location).broadcast(callable);
    if (results.size() != 1) {
      throw new IllegalStateException("Multiple response for the Execution on " + hostname);
    }
    return results.iterator().next();
  }

  public void install() {
    logger.info("starting TMS on {}", tmsHostname);
    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));

    logger.info("Setting up locally the extracted install to be deployed remotely");
    localKitManager.setupLocalInstall(license, offline);

    logger.info("Attempting to remotely installing if existing install already exists on {}", tmsHostname);
    boolean isRemoteInstallationSuccessful = executeRemotely(tmsHostname, () -> Agent.CONTROLLER.attemptRemoteTmsInstallation(
        instanceId, tmsHostname, distribution, offline, license, securityConfig, localKitManager.getKitInstallationName()));
    if (!isRemoteInstallationSuccessful) {
      IgniteHelper.checkAgentHealth(ignite, tmsHostname);
      try {
        ClusterGroup location = ignite.cluster().forAttribute("nodename", tmsHostname);
        final BlockingQueue<Object> queue = ignite.queue(instanceId + "@file-transfer-queue@tsa", 100, new CollectionConfiguration());
        IgniteFuture<Void> remoteDownloadFuture = ignite.compute(location)
            .broadcastAsync((IgniteRunnable)() -> Agent.CONTROLLER.downloadKit(instanceId, distribution, localKitManager
                .getKitInstallationName()));
        uploadFile(queue, localKitManager.getKitInstallationPath(), null);
        queue.put(Boolean.TRUE); // end of upload marker

        remoteDownloadFuture.get();

        executeRemotely(tmsHostname, TIMEOUT, () -> Agent.CONTROLLER.installTms(instanceId, tmsHostname, distribution, license, securityConfig, localKitManager.getKitInstallationName(), tcEnv));
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + tmsHostname, e);
      }
    }

  }

  public void stopTms(final String tmsHostname) {
    TerracottaManagementServerState terracottaManagementServerState = getTmsState(tmsHostname);
    if (terracottaManagementServerState == TerracottaManagementServerState.STOPPED) {
      return;
    }
    if (terracottaManagementServerState != TerracottaManagementServerState.STARTED) {
      throw new IllegalStateException("Cannot stop: TMS server , already in state " + terracottaManagementServerState);
    }

    logger.info("stopping on {}", tmsHostname);
    executeRemotely(tmsHostname, TIMEOUT, () -> Agent.CONTROLLER.stopTms(instanceId));
  }

  public TerracottaManagementServerState getTmsState(final String tmsHostname) {
    return executeRemotely(tmsHostname, () -> Agent.CONTROLLER.getTerracottaManagementServerState(instanceId));
  }

  public void start() {
    logger.info("starting TMS on {}", tmsHostname);
    executeRemotely(tmsHostname, TIMEOUT, () -> Agent.CONTROLLER.startTms(instanceId));
  }

  private void uploadFile(BlockingQueue<Object> queue, File file, String path) throws InterruptedException, IOException {
    FileMetadata fileMetadata = new FileMetadata(path, file);
    if (!file.exists()) {
      logger.debug("skipping upload of non-existent classpath entry {}", fileMetadata);
      return;
    }
    queue.put(fileMetadata);
    logger.debug("uploading {}", fileMetadata);

    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File _file : files) {
        String parentPath = path == null ? "" : path + "/";
        uploadFile(queue, _file, parentPath + file.getName());
      }
    } else {
      byte[] buffer = new byte[64 * 1024];
      try (FileInputStream fis = new FileInputStream(file)) {
        while (true) {
          int read = fis.read(buffer);
          if (read < 0) {
            break;
          }
          byte[] toSend;
          if (read != buffer.length) {
            toSend = new byte[read];
            System.arraycopy(buffer, 0, toSend, 0, read);
          } else {
            toSend = buffer;
          }
          queue.put(toSend);
        }
      }
      logger.debug("uploaded {}", fileMetadata);
    }
  }

}
