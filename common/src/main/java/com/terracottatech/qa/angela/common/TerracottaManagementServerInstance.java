package com.terracottatech.qa.angela.common;

import com.terracottatech.qa.angela.common.distribution.DistributionController;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class TerracottaManagementServerInstance {

  private final DistributionController distributionController;
  private final File location;
  private volatile TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaManagementServerInstanceProcess = new TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess(new AtomicReference<>(TerracottaManagementServerState.STOPPED));

  public TerracottaManagementServerInstance(final DistributionController distributionController, final File location) {
    this.distributionController = distributionController;
    this.location = location;
  }

  public void start() {
    this.terracottaManagementServerInstanceProcess = this.distributionController.startTms(location);
  }

  public void stop() {
    this.distributionController.stopTms(location, terracottaManagementServerInstanceProcess);
  }

  public TerracottaManagementServerState getTerracottaManagementServerState() {
    return this.terracottaManagementServerInstanceProcess.getState();
  }


  public static class TerracottaManagementServerInstanceProcess {
    private final int[] pids;
    private final AtomicReference<TerracottaManagementServerState> state;

    public TerracottaManagementServerInstanceProcess(AtomicReference<TerracottaManagementServerState> state, int... pids) {
      this.pids = pids;
      this.state = state;
    }

    public TerracottaManagementServerState getState() {
      return state.get();
    }

    public int[] getPids() {
      return pids;
    }
  }

}
