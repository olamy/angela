package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.http.HttpUtils;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import io.restassured.path.json.JsonPath;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;

public class Tms implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Tsa.class);
  private static final long TIMEOUT = 60000;
  private static final String API_CONNECTIONS_PROBE_OLD = "/api/connections/probe/";
  private static final String API_CONNECTIONS_PROBE_NEW = "/api/connections/probe?uri=";
  private final String tmsHostname;
  private final String kitInstallationPath;
  private final Distribution distribution;
  private boolean closed = false;
  private final Ignite ignite;
  private final License license;
  private final InstanceId instanceId;
  private final TmsServerSecurityConfig securityConfig;

  public Tms(Ignite ignite, InstanceId instanceId, License license, String hostname, Distribution distribution, TmsServerSecurityConfig securityConfig) {
    this.distribution = distribution;
    if (license == null) {
      throw new IllegalArgumentException("TMS requires a license.");
    }
    this.license = license;
    this.instanceId = instanceId;
    this.ignite = ignite;
    this.tmsHostname = hostname;
    this.kitInstallationPath = System.getProperty("kitInstallationPath");
    this.securityConfig = securityConfig;
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
    uninstall();
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
    executeRemotely(tmsHostname, TIMEOUT, () -> Agent.CONTROLLER.uninstallTms(instanceId, distribution, kitInstallationPath, tmsHostname));
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

    executeRemotely(tmsHostname, TIMEOUT, () -> Agent.CONTROLLER.installTms(instanceId, tmsHostname, distribution, kitInstallationPath, license, securityConfig));
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

}
