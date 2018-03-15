package com.terracottatech.qa.angela.common;


import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.zeroturnaround.exec.StartedProcess;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;
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

  public void configureLicense(final InstanceId instanceId, final License license, final TcConfig[] tcConfigs, SecurityRootDirectory securityRootDirectory) {
    this.distributionController.configureLicense(instanceId, location, license, tcConfigs, securityRootDirectory);
  }

  public void waitForState(Predicate<TerracottaServerState> condition) {
    while (!condition.test(getTerracottaServerState())) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public TerracottaServerState getTerracottaServerState() {
    return this.terracottaServerInstanceProcess.getState();
  }

  public static class TerracottaServerInstanceProcess {
    private final Number[] pids;
    private final AtomicReference<TerracottaServerState> state;

    public TerracottaServerInstanceProcess(AtomicReference<TerracottaServerState> state, Number ... pids) {
      this.pids = pids;
      this.state = state;
    }

    public TerracottaServerState getState() {
      return state.get();
    }

    public int[] getPids() {
      return stream(pids).mapToInt(Number::intValue).toArray();
    }
  }
}
