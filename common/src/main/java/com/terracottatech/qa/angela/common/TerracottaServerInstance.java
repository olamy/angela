package com.terracottatech.qa.angela.common;


import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.Processes;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static java.util.Arrays.stream;

/**
 * Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServerInstance  {

  private final ServerSymbolicName serverSymbolicName;
  private final DistributionController distributionController;
  private final File location;
  private volatile TerracottaServerInstanceProcess terracottaServerInstanceProcess = new TerracottaServerInstanceProcess(new AtomicReference<>(TerracottaServerState.STOPPED));

  public TerracottaServerInstance(final ServerSymbolicName serverSymbolicName, final DistributionController distributionController, final File location) {
    this.serverSymbolicName = serverSymbolicName;
    this.distributionController = distributionController;
    this.location = location;
  }

  public void create() {
    this.terracottaServerInstanceProcess = this.distributionController.create(serverSymbolicName, location);
  }

  public void stop() {
    this.distributionController.stop(serverSymbolicName, location, terracottaServerInstanceProcess);
  }

  public void configureLicense(String clusterName, String licensePath, final TcConfig[] tcConfigs, SecurityRootDirectory securityRootDirectory) {
    this.distributionController.configureLicense(clusterName, location, licensePath, tcConfigs, securityRootDirectory);
  }

  public ClusterToolExecutionResult clusterTool(String... arguments) {
    return distributionController.invokeClusterTool(location, arguments);
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
    return this.terracottaServerInstanceProcess.getState();
  }

  public static class TerracottaServerInstanceProcess {
    private final Number[] pids;
    private final AtomicReference<TerracottaServerState> state;

    public TerracottaServerInstanceProcess(AtomicReference<TerracottaServerState> state, Number... pids) {
      for (Number pid : pids) {
        if (pid.intValue() < 1) {
          throw new IllegalArgumentException("Pid cannot be < 1");
        }
      }
      this.pids = pids;
      this.state = state;
    }

    public TerracottaServerState getState() {
      return state.get();
    }

    public int[] getPids() {
      return stream(pids).mapToInt(Number::intValue).toArray();
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
        throw new RuntimeException("Error checking liveness of a process instance with PIDs " + Arrays.toString(pids), e);
      }
    }
  }
}
