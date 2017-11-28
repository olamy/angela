package com.terracottatech.qa.angela.common;


import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.ServerLogOutputStream;
import org.zeroturnaround.exec.StartedProcess;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServerInstance  {

  private static final TerracottaServerInstanceProcess TSIP_STOPPED = new TerracottaServerInstanceProcess(null, null, null, TerracottaServerState.STOPPED);

  private final ServerSymbolicName serverSymbolicName;
  private final DistributionController distributionController;
  private final File location;
  private volatile TerracottaServerInstanceProcess terracottaServerInstanceProcess = TSIP_STOPPED;

  public TerracottaServerInstance(final ServerSymbolicName serverSymbolicName, final DistributionController distributionController, final File location) {
    this.serverSymbolicName = serverSymbolicName;
    this.distributionController = distributionController;
    this.location = location;
  }

  public TerracottaServerState start() {
    this.terracottaServerInstanceProcess = this.distributionController.start(serverSymbolicName, location);
    // spawn a thread that resets terracottaServerInstanceProcess to null when the TC server process dies
    Thread processWatcher = new Thread(() -> {
      try {
        terracottaServerInstanceProcess.startedProcess.getFuture().get();
        terracottaServerInstanceProcess = TSIP_STOPPED;
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    });
    processWatcher.setDaemon(true);
    processWatcher.start();
    return this.terracottaServerInstanceProcess.getState();
  }

  public TerracottaServerState stop() {
    return this.distributionController.stop(serverSymbolicName, location, terracottaServerInstanceProcess);
  }

  public void configureLicense(final InstanceId instanceId, final License license, final TcConfig[] tcConfigs) {
    this.distributionController.configureLicense(instanceId, location, license, tcConfigs);
  }

  public TerracottaServerState getTerracottaServerState() {
    return this.terracottaServerInstanceProcess.getState();
  }

  public static class TerracottaServerInstanceProcess {
    private final StartedProcess startedProcess;
    private final AtomicInteger pid;
    private final ServerLogOutputStream logs;
    private final TerracottaServerState state;

    public TerracottaServerInstanceProcess(final StartedProcess startedProcess, final AtomicInteger pid, final ServerLogOutputStream logs, final TerracottaServerState state) {
      this.startedProcess = startedProcess;
      this.pid = pid;
      this.logs = logs;
      this.state = state;
    }

    public TerracottaServerState getState() {
      return state;
    }

    public StartedProcess getStartedProcess() {
      return startedProcess;
    }

    public ServerLogOutputStream getLogs() {
      return logs;
    }

    public AtomicInteger getPid() {
      return pid;
    }
  }
}
