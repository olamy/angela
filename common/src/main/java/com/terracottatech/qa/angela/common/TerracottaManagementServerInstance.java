package com.terracottatech.qa.angela.common;

import com.terracottatech.qa.angela.common.distribution.DistributionController;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class TerracottaManagementServerInstance {

  private final DistributionController distributionController;
  private final File location;
  private final TerracottaCommandLineEnvironment tcEnv;
  private volatile TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaManagementServerInstanceProcess = new TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess(new AtomicReference<>(TerracottaManagementServerState.STOPPED));

  public TerracottaManagementServerInstance(final DistributionController distributionController, final File location, TerracottaCommandLineEnvironment tcEnv) {
    this.distributionController = distributionController;
    this.location = location;
    this.tcEnv = tcEnv;
  }

  public void start() {
    this.terracottaManagementServerInstanceProcess = this.distributionController.startTms(location, tcEnv);
  }

  public void stop() {
    this.distributionController.stopTms(location, terracottaManagementServerInstanceProcess, tcEnv);
  }

  public TerracottaManagementServerState getTerracottaManagementServerState() {
    return this.terracottaManagementServerInstanceProcess.getState();
  }


  public static class TerracottaManagementServerInstanceProcess {
    private final Set<Number> pids;
    private final AtomicReference<TerracottaManagementServerState> state;

    public TerracottaManagementServerInstanceProcess(AtomicReference<TerracottaManagementServerState> state, Number... pids) {
      for (Number pid : pids) {
        if (pid.intValue() < 1) {
          throw new IllegalArgumentException("Pid cannot be < 1");
        }
      }
      this.pids = new HashSet<>(Arrays.asList(pids));
      this.state = state;
    }

    public TerracottaManagementServerState getState() {
      return state.get();
    }

    public Set<Number> getPids() {
      return Collections.unmodifiableSet(pids);
    }
  }

}
