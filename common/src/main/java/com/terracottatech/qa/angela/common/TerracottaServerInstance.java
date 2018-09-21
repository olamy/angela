package com.terracottatech.qa.angela.common;


import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.DisruptionProviderFactory;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.net.GroupMember;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.metrics.HardwareMetricsCollector;

import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.Processes;

import java.io.Closeable;
import java.io.File;
import java.net.InetSocketAddress;
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
  private static final DisruptionProvider DISRUPTION_PROVIDER = DisruptionProviderFactory.getDefault();
  private final ServerSymbolicName serverSymbolicName;
  private final DistributionController distributionController;
  private final File location;
  private final TcConfig tcConfig;
  private volatile TerracottaServerInstanceProcess terracottaServerInstanceProcess;
  private final Map<ServerSymbolicName, Disruptor> disruptionLinks = new ConcurrentHashMap<>();
  private final boolean netDisruptionEnabled;

  public TerracottaServerInstance(final ServerSymbolicName serverSymbolicName, final DistributionController distributionController, final File location, final TcConfig tcConfig, final boolean netDisruptionEnabled) {
    this.serverSymbolicName = serverSymbolicName;
    this.distributionController = distributionController;
    this.location = location;
    this.netDisruptionEnabled = netDisruptionEnabled;

    this.tcConfig = TcConfig.copy(tcConfig);
    constructNetDisruptionLinks();
    this.tcConfig.substituteToken(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
    String modifiedTcConfigName = this.tcConfig.getTcConfigName()
                                      .substring(0, this.tcConfig.getTcConfigName()
                                                        .length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
    this.tcConfig.writeTcConfigFile(location, modifiedTcConfigName);
  }

  public void create(TerracottaCommandLineEnvironment env, final HardwareMetricsCollector.TYPE type) {
    this.terracottaServerInstanceProcess = this.distributionController.create(serverSymbolicName, location, tcConfig, env, type);
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
    this.distributionController.stop(serverSymbolicName, location, terracottaServerInstanceProcess, tcEnv);
  }


  @Override
  public void close() {
    removeDisruptionLinks();
  }

  public void configureLicense(String clusterName, String licensePath, final TcConfig[] tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment env, boolean verbose) {
    this.distributionController.configureLicense(clusterName, location, licensePath, tcConfigs, securityRootDirectory, env, verbose);
  }

  public ClusterToolExecutionResult clusterTool(TerracottaCommandLineEnvironment env, String... arguments) {
    return distributionController.invokeClusterTool(location, env, arguments);
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

  public static class TerracottaServerInstanceProcess {
    private final Set<Number> pids;
    private final AtomicReference<TerracottaServerState> state;
    private final HardwareMetricsCollector hardwareMetricsCollector;

    public TerracottaServerInstanceProcess(AtomicReference<TerracottaServerState> state, final HardwareMetricsCollector hardwareMetricsCollector, Number... pids) {
      for (Number pid : pids) {
        if (pid.intValue() < 1) {
          throw new IllegalArgumentException("Pid cannot be < 1");
        }
      }
      this.hardwareMetricsCollector = hardwareMetricsCollector;
      this.pids = new HashSet<>(Arrays.asList(pids));
      this.state = state;
    }

    public TerracottaServerState getState() {
      return state.get();
    }

    public Set<Number> getPids() {
      return Collections.unmodifiableSet(pids);
    }

    public HardwareMetricsCollector getHardwareMetricsCollector() {
      return hardwareMetricsCollector;
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
