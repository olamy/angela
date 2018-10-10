package com.terracottatech.qa.angela.common;


import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.DisruptionProviderFactory;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.net.GroupMember;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.Processes;

import java.io.Closeable;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServerInstance implements Closeable {
  private final static Logger logger = LoggerFactory.getLogger(TerracottaServerInstance.class);
  private static final DisruptionProvider DISRUPTION_PROVIDER = DisruptionProviderFactory.getDefault();
  private final ServerSymbolicName serverSymbolicName;
  private final DistributionController distributionController;
  private final File installLocation;
  private final Distribution distribution;
  private final File licenseFileLocation;
  private final TcConfig tcConfig;
  private volatile TerracottaServerInstanceProcess terracottaServerInstanceProcess;
  private final Map<ServerSymbolicName, Disruptor> disruptionLinks = new ConcurrentHashMap<>();
  private final boolean netDisruptionEnabled;

  public TerracottaServerInstance(ServerSymbolicName serverSymbolicName, File installLocation, TcConfig tcConfig, boolean netDisruptionEnabled, int stripeId, SecurityRootDirectory securityRootDirectory, License license, Distribution distribution) {
    this.serverSymbolicName = serverSymbolicName;
    this.distributionController = distribution.createDistributionController();
    this.installLocation = installLocation;
    this.distribution = distribution;
    this.licenseFileLocation = new File(installLocation, license.getFilename());
    this.netDisruptionEnabled = netDisruptionEnabled;

    this.tcConfig = TcConfig.copy(tcConfig);
    constructNetDisruptionLinks();
    this.tcConfig.substituteToken(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
    String modifiedTcConfigName = this.tcConfig.getTcConfigName()
                                      .substring(0, this.tcConfig.getTcConfigName()
                                                        .length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
    this.tcConfig.updateLogsLocation(installLocation, stripeId);
    setupSecurityDirectories(securityRootDirectory, stripeId);
    // all config mutations must happen before this line as the file gets written to disk here
    this.tcConfig.writeTcConfigFile(installLocation, modifiedTcConfigName);
  }

  private void setupSecurityDirectories(SecurityRootDirectory securityRootDirectory, int stripeId) {
    if (securityRootDirectory != null) {
      installSecurityRootDirectory(securityRootDirectory);
      createAuditDirectory(installLocation, stripeId);
    }
  }

  private void installSecurityRootDirectory(SecurityRootDirectory securityRootDirectory) {
    final String serverName = serverSymbolicName.getSymbolicName();
    Path securityRootDirectoryPath = installLocation.toPath().resolve("security-root-directory-" + serverName);
    logger.info("Installing SecurityRootDirectory in {} for server {}", securityRootDirectoryPath, serverName);
    securityRootDirectory.createSecurityRootDirectory(securityRootDirectoryPath);
    tcConfig.updateSecurityRootDirectoryLocation(securityRootDirectoryPath.toString());
  }

  private void createAuditDirectory(File installLocation, int stripeId) {
    this.tcConfig.updateAuditDirectoryLocation(installLocation, stripeId);
  }

  public Distribution getDistribution() {
    return distribution;
  }

  public void create(TerracottaCommandLineEnvironment env) {
    this.terracottaServerInstanceProcess = this.distributionController.createTsa(serverSymbolicName, installLocation, tcConfig, env);
  }

  public void disrupt(Collection<TerracottaServer> targets) {
    if (!netDisruptionEnabled) {
      throw new IllegalArgumentException("Topology not enabled for network disruption");
    }
    for (TerracottaServer server : targets) {
      disruptionLinks.get(server.getServerSymbolicName()).disrupt();
    }
  }

  public void undisrupt(Collection<TerracottaServer> targets) {
    if (!netDisruptionEnabled) {
      throw new IllegalArgumentException("Topology not enabled for network disruption");
    }
    for (TerracottaServer target : targets) {
      disruptionLinks.get(target.getServerSymbolicName()).undisrupt();
    }
  }

  public void stop(TerracottaCommandLineEnvironment tcEnv) {
    this.distributionController.stopTsa(serverSymbolicName, tcConfig, installLocation, terracottaServerInstanceProcess, tcEnv);
  }


  @Override
  public void close() {
    removeDisruptionLinks();
  }

  public void configureTsaLicense(String clusterName, String licensePath, List<TcConfig> tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment env, boolean verbose) {
    this.distributionController.configureTsaLicense(clusterName, installLocation, licensePath, tcConfigs, securityRootDirectory, env, verbose);
  }

  public ClusterToolExecutionResult clusterTool(TerracottaCommandLineEnvironment env, String... arguments) {
    return distributionController.invokeClusterTool(installLocation, env, arguments);
  }

  public void waitForState(Predicate<TerracottaServerState> condition) {
    while (this.terracottaServerInstanceProcess.isAlive() && !condition.test(getTerracottaServerState())) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    if (!this.terracottaServerInstanceProcess.isAlive()) {
      throw new RuntimeException("TC server died while waiting on state-change condition");
    }
  }

  public TerracottaServerState getTerracottaServerState() {
    if (this.terracottaServerInstanceProcess == null) {
      return TerracottaServerState.STOPPED;
    } else {
      return this.terracottaServerInstanceProcess.getState();
    }
  }

  public File getInstallLocation() {
    return installLocation;
  }

  public File getLicenseFileLocation() {
    return licenseFileLocation;
  }

  public static class TerracottaServerInstanceProcess {
    private final Set<Number> pids;
    private final AtomicReference<TerracottaServerState> state;

    public TerracottaServerInstanceProcess(AtomicReference<TerracottaServerState> state, Number... pids) {
      for (Number pid : pids) {
        if (pid.intValue() < 1) {
          throw new IllegalArgumentException("Pid cannot be < 1");
        }
      }
      this.pids = new HashSet<>(Arrays.asList(pids));
      this.state = state;
    }

    public TerracottaServerState getState() {
      return state.get();
    }

    public Set<Number> getPids() {
      return Collections.unmodifiableSet(pids);
    }

    public boolean isAlive() {
      try {
        // if at least one PID is alive, the process is considered alive
        for (Number pid : pids) {
          PidProcess pidProcess = Processes.newPidProcess(pid.intValue());
          if (pidProcess.isAlive()) {
            return true;
          }
        }
        return false;
      } catch (Exception e) {
        throw new RuntimeException("Error checking liveness of a process instance with PIDs " + pids, e);
      }
    }
  }

  /**
   * Construct net disruption link between this server and other servers in stripe
   */
  private void constructNetDisruptionLinks() {
    if (this.netDisruptionEnabled) {
      List<GroupMember> members = tcConfig.retrieveGroupMembers(serverSymbolicName.getSymbolicName(), DISRUPTION_PROVIDER
          .isProxyBased());
      GroupMember thisMember = members.get(0);
      for (int i = 1; i < members.size(); ++i) {
        GroupMember otherMember = members.get(i);
        final InetSocketAddress src = new InetSocketAddress(thisMember.getHost(), otherMember.isProxiedMember() ? otherMember
            .getProxyPort() : thisMember.getGroupPort());
        final InetSocketAddress dest = new InetSocketAddress(otherMember.getHost(), otherMember.getGroupPort());
        disruptionLinks.put(new ServerSymbolicName(otherMember.getServerName()), DISRUPTION_PROVIDER.createLink(src, dest));
      }
    }
  }

  private void removeDisruptionLinks() {
    if (this.netDisruptionEnabled) {
      disruptionLinks.values().forEach(DISRUPTION_PROVIDER::removeLink);
    }
  }
}
