package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.TmsConfig;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;

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


  public Tms(Ignite ignite, InstanceId instanceId, License license, String hostname, Distribution distribution) {
    this.distribution = distribution;
    if (license == null) {
      throw new IllegalArgumentException("TMS requires a license.");
    }
    this.license = license;
    this.instanceId = instanceId;
    this.ignite = ignite;
    this.tmsHostname = hostname;
    this.kitInstallationPath = System.getProperty("kitInstallationPath");


  }

  public void connectToCluster(URI uri) {
    logger.info("connecting TMS to {}", uri.toString());

    // probe
    try {
      String url = "http://" + tmsHostname + ":9480/api/connections/probe/" +
          URLEncoder.encode(uri.toString(), "UTF-8");

      URL obj = new URL(url);
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      int responseCode = con.getResponseCode();
      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuilder response = new StringBuilder();
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();
      logger.info("tms probe result :" + response.toString());

      // connect
      url = "http://" + tmsHostname + ":9480/api/connections";
      obj = new URL(url);
      con = (HttpURLConnection) obj.openConnection();

      //add reuqest header
      con.setRequestMethod("POST");
      con.setRequestProperty("Accept", "application/json");
      con.setRequestProperty("content-type", "application/json");

      // Send post request
      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.writeBytes(response.toString());
      wr.flush();
      wr.close();

      responseCode = con.getResponseCode();

      in = new BufferedReader(
          new InputStreamReader(con.getInputStream()));
      response = new StringBuilder();

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();

      //print result
      logger.info("tms connect result :" + response.toString());


    } catch (Exception e) {

    }
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
        (IgniteRunnable) () -> Agent.CONTROLLER.installTms(instanceId, tmsHostname, distribution, kitInstallationPath, license));
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
