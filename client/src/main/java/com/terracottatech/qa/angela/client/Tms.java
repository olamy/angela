package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.http.HttpUtils;
import com.terracottatech.qa.angela.common.http.HttpsUtils;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
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
import java.util.HashMap;
import java.util.Map;

public class Tms implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Tsa.class);
  private static final long TIMEOUT = 60000;
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
   * If cluster security is enabled it will connect to the cluster via SSL/TLS, otherwise if connects via plain text.
   *
   * @param uri of the cluster to connect to
   * @return connectionName
   */
  public String connectToCluster(URI uri) {
    String connectionName;
    logger.info("connecting TMS to {}", uri.toString());
    // probe
    String url;
    try {
      url = "http://" + tmsHostname + ":9480/api/connections/probe/" +
          URLEncoder.encode(uri.toString(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Could not encode terracotta connection url", e);
    }
    String response = HttpUtils.sendGetRequest(url);
    logger.info("tms probe result :" + response.toString());

    // create connection
    url = "http://" + tmsHostname + ":9480/api/connections";
    Map<String, String> headers =  new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("content-type", "application/json");
    response = HttpUtils.sendPostRequest(url, response, headers);
    logger.info("tms connect result :" + response.toString());

    connectionName = JsonPath.from(response).get("config.connectionName");

    return connectionName;
  }

  /**
   *
   * This method connects to the TMS via HTTPS (securely) REST calls.  It also creates a TMS connection to the cluster.
   * If cluster security is enabled it will connect to the cluster via SSL/TLS, otherwise if connects via plain text.
   *
   * @param uri of the cluster to connect to
   * @return connectionName
   */
  public String connectToCluster(URI uri, TmsClientSecurityConfig tmsClientSecurityConfig) throws Exception {
    String connectionName;
    logger.info("connecting TMS to {}", uri.toString());
    // probe
    String url;
    try {
      url = "https://" + tmsHostname + ":9480/api/connections/probe/" +
          URLEncoder.encode(uri.toString(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Could not encode terracotta connection url", e);
    }
    String response = HttpsUtils.sendGetRequest(url, tmsClientSecurityConfig);
    logger.info("tms probe result :" + response.toString());

    // create connection
    url = "https://" + tmsHostname + ":9480/api/connections";
    Map<String, String> headers =  new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("content-type", "application/json");
    response = HttpsUtils.sendPostRequest(url, response, headers, tmsClientSecurityConfig);
    logger.info("tms connect result :" + response.toString());

    connectionName = JsonPath.from(response).get("config.connectionName");

    return connectionName;
  }

  public RemoteFolder browse(String root) {
    String path =
        executeRemotely(tmsHostname, (IgniteCallable<String>) () -> Agent.CONTROLLER.getTmsInstallationPath(instanceId));
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
    executeRemotely(tmsHostname, TIMEOUT, (IgniteRunnable) () ->
        Agent.CONTROLLER.uninstallTms(instanceId, distribution, kitInstallationPath, tmsHostname));
  }

  private void stop() {
    stopTms(tmsHostname);
  }

  private void executeRemotely(final String hostname, final long timeout, final IgniteRunnable runnable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    ignite.compute(location).withTimeout(timeout).broadcast(runnable);
  }

  private <R> R executeRemotely(final String hostname, final IgniteCallable<R> callable) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    Collection<R> results = ignite.compute(location).broadcast(callable);
    if (results.size() != 1) {
      throw new IllegalStateException("Multiple response for the Execution on " + hostname);
    }
    return results.iterator().next();
  }

  public void install() {
    logger.info("starting TMS on {}", tmsHostname);

    executeRemotely(tmsHostname, TIMEOUT,
        (IgniteRunnable) () -> Agent.CONTROLLER.installTms(instanceId, tmsHostname, distribution, kitInstallationPath, license, securityConfig));
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
    executeRemotely(tmsHostname, TIMEOUT,
        (IgniteRunnable) () -> Agent.CONTROLLER.stopTms(instanceId));
  }

  public TerracottaManagementServerState getTmsState(final String tmsHostname) {
    return executeRemotely(tmsHostname, () ->
        Agent.CONTROLLER.getTerracottaManagementServerState(instanceId));
  }

  public void start() {
    logger.info("starting TMS on {}", tmsHostname);
    executeRemotely(tmsHostname, TIMEOUT,
        (IgniteRunnable) () -> Agent.CONTROLLER.startTms(instanceId));
  }

}
