package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.config.TmsConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tms implements AutoCloseable {

  private final static Logger logger = LoggerFactory.getLogger(Tsa.class);
  private final TmsConfigurationContext tmsConfigurationContext;
  private boolean closed = false;
  private final Ignite ignite;
  private final InstanceId instanceId;
  private final LocalKitManager localKitManager;

  @Deprecated
  private static final String NONE = "none";
  @Deprecated
  private static final String BROWSER_SECURITY = "browser-security";
  @Deprecated
  private static final String CLUSTER_SECURITY = "cluster-security";
  @Deprecated
  public static final String FULL = "full";

  Tms(Ignite ignite, InstanceId instanceId, TmsConfigurationContext tmsConfigurationContext) {
    this.tmsConfigurationContext = tmsConfigurationContext;
    this.instanceId = instanceId;
    this.ignite = ignite;
    this.localKitManager = new LocalKitManager(tmsConfigurationContext.getDistribution());
    install();
  }

  public TmsConfigurationContext getTmsConfigurationContext() {
    return tmsConfigurationContext;
  }

  public String url() {
    boolean isHttps = false;
    TmsServerSecurityConfig tmsServerSecurityConfig = tmsConfigurationContext.getSecurityConfig();
    if (tmsServerSecurityConfig != null) {
      isHttps = ("true".equals(tmsServerSecurityConfig.getTmsSecurityHttpsEnabled())
                 || FULL.equals(tmsServerSecurityConfig.getDeprecatedSecurityLevel())
                 || BROWSER_SECURITY.equals(tmsServerSecurityConfig.getDeprecatedSecurityLevel())
      );
    }
    return (isHttps ? "https://" : "http://") + tmsConfigurationContext.getHostname() + ":9480";
  }

  public TmsHttpClient httpClient() {
    return httpClient(null);
  }

  public TmsHttpClient httpClient(TmsClientSecurityConfig tmsClientSecurityConfig) {
    return new TmsHttpClient(url(), tmsClientSecurityConfig);
  }

  public RemoteFolder browse(String root) {
    String tmsHostname = tmsConfigurationContext.getHostname();
    String path = IgniteClientHelper.executeRemotely(ignite, tmsHostname, () -> Agent.controller.getTmsInstallationPath(instanceId));
    return new RemoteFolder(ignite, tmsHostname, path, root);
  }

  public TerracottaManagementServerState getTmsState() {
    return IgniteClientHelper.executeRemotely(ignite, tmsConfigurationContext.getHostname(), () -> Agent.controller.getTmsState(instanceId));
  }

  public Tms start() {
    String tmsHostname = tmsConfigurationContext.getHostname();
    logger.info("Starting TMS on {}", tmsHostname);
    IgniteClientHelper.executeRemotely(ignite, tmsHostname, () -> Agent.controller.startTms(instanceId));
    return this;
  }

  public void stop() {
    stopTms(tmsConfigurationContext.getHostname());
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
    String tmsHostname = tmsConfigurationContext.getHostname();
    TerracottaManagementServerState terracottaServerState = getTmsState();
    if (terracottaServerState == null) {
      return;
    }
    if (terracottaServerState != TerracottaManagementServerState.STOPPED) {
      throw new IllegalStateException("Cannot uninstall: server " + tmsHostname + " already in state " + terracottaServerState);
    }

    logger.info("Uninstalling TMS from {}", tmsHostname);
    IgniteClientHelper.executeRemotely(ignite, tmsHostname, () -> Agent.controller.uninstallTms(instanceId, tmsConfigurationContext.getDistribution(), localKitManager
        .getKitInstallationName(), tmsHostname));
  }

  private void install() {
    String tmsHostname = tmsConfigurationContext.getHostname();
    License license = tmsConfigurationContext.getLicense();
    Distribution distribution = tmsConfigurationContext.getDistribution();
    TmsServerSecurityConfig tmsServerSecurityConfig = tmsConfigurationContext.getSecurityConfig();
    TerracottaCommandLineEnvironment tcEnv = tmsConfigurationContext.getTerracottaCommandLineEnvironment();

    logger.info("starting TMS on {}", tmsHostname);
    boolean offline = Boolean.parseBoolean(System.getProperty("offline", "false"));

    logger.debug("Setting up locally the extracted install to be deployed remotely");
    String kitInstallationPath = System.getProperty("kitInstallationPath");
    localKitManager.setupLocalInstall(license, kitInstallationPath, offline);

    logger.info("Attempting to remotely install if distribution already exists on {}", tmsHostname);
    boolean isRemoteInstallationSuccessful;
    if (kitInstallationPath == null) {
      isRemoteInstallationSuccessful = IgniteClientHelper.executeRemotely(ignite, tmsHostname, () -> Agent.controller.installTms(
          instanceId, tmsHostname, distribution, offline, license, tmsServerSecurityConfig, localKitManager.getKitInstallationName(), tcEnv));
    } else {
      isRemoteInstallationSuccessful = false;
    }
    if (!isRemoteInstallationSuccessful) {
      try {
        IgniteClientHelper.uploadKit(ignite, tmsHostname, instanceId, distribution,
            localKitManager.getKitInstallationName(), localKitManager.getKitInstallationPath().toFile());

        IgniteClientHelper.executeRemotely(ignite, tmsHostname, () -> Agent.controller.installTms(instanceId, tmsHostname,
            distribution, offline, license, tmsServerSecurityConfig, localKitManager.getKitInstallationName(), tcEnv));
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload kit to " + tmsHostname, e);
      }
    }

  }

  private void stopTms(String tmsHostname) {
    TerracottaManagementServerState terracottaManagementServerState = getTmsState();
    if (terracottaManagementServerState == TerracottaManagementServerState.STOPPED) {
      return;
    }
    if (terracottaManagementServerState != TerracottaManagementServerState.STARTED) {
      throw new IllegalStateException("Cannot stop: TMS server , already in state " + terracottaManagementServerState);
    }

    logger.info("Stopping TMS on {}", tmsHostname);
    IgniteClientHelper.executeRemotely(ignite, tmsHostname, () -> Agent.controller.stopTms(instanceId));
  }

}
