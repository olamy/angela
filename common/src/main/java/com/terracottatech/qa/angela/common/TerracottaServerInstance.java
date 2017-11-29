package com.terracottatech.qa.angela.common;


import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

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

  public void start() {
    this.terracottaServerInstanceProcess = this.distributionController.start(serverSymbolicName, location);
  }

  public void stop() {
    this.distributionController.stop(serverSymbolicName, location, terracottaServerInstanceProcess);
  }

  public void configureLicense(final InstanceId instanceId, final License license, final TcConfig[] tcConfigs) {
    this.distributionController.configureLicense(instanceId, location, license, tcConfigs);
  }

  public TerracottaServerState getTerracottaServerState() {
    return this.terracottaServerInstanceProcess.getState();
  }

  public static class TerracottaServerInstanceProcess {
    private final int[] pids;
    private final AtomicReference<TerracottaServerState> state;

    public TerracottaServerInstanceProcess(AtomicReference<TerracottaServerState> state, int... pids) {
      this.pids = pids;
      this.state = state;
    }

    public TerracottaServerState getState() {
      return state.get();
    }

    public int[] getPids() {
      return pids;
    }
  }
}
