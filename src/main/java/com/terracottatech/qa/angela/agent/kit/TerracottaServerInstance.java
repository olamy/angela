package com.terracottatech.qa.angela.agent.kit;


import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.StartedProcess;

import com.terracottatech.qa.angela.agent.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServerInstance  {

  private static final Logger logger = LoggerFactory.getLogger(TerracottaServerInstance.class);
  private final ServerSymbolicName serverSymbolicName;
  private final DistributionController distributionController;
  private final File location;
  private TerracottaServerInstanceProcess terracottaServerInstanceProcess;

  public TerracottaServerInstance(final ServerSymbolicName serverSymbolicName, final DistributionController distributionController, final File location) {
    this.serverSymbolicName = serverSymbolicName;
    this.distributionController = distributionController;
    this.location = location;
  }

  public TerracottaServerState start() {
    this.terracottaServerInstanceProcess = this.distributionController.start(serverSymbolicName, location);
    return this.terracottaServerInstanceProcess.getState();
  }

  public TerracottaServerState stop() {
    return this.distributionController.stop(serverSymbolicName, location, terracottaServerInstanceProcess);
  }

  public void configureLicense(final String topologyId, final License license, final TcConfig[] tcConfigs) {
    this.distributionController.configureLicense(topologyId, location, license, tcConfigs);
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
