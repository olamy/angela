package com.terracottatech.qa.angela.common.util;

import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import org.zeroturnaround.exec.StartedProcess;

import java.util.concurrent.atomic.AtomicReference;

import static com.terracottatech.qa.angela.common.TerracottaManagementServerState.STARTED;

/**
 * @author Anthony Dahanne
 */

public class TerracottaManagementServerLogOutputStream extends LogOutputStream {
  private final boolean fullLogs = Boolean.getBoolean("angela.tms.log.full");
  private final AtomicReference<TerracottaManagementServerState> stateRef;
  private volatile int pid = -1;

  public TerracottaManagementServerLogOutputStream(AtomicReference<TerracottaManagementServerState> stateRef) {
    this.stateRef = stateRef;
  }

  @Override
  protected void processLine(final String line) {
    if (fullLogs || line.contains("WARN") || line.contains("ERROR")) {
      System.out.println("[TMS] " + line);
    }
    if (line.contains("started on port")) {
      stateRef.set(STARTED);
    }
    if (line.contains("Starting TmsApplication")) {
      int startIdx = line.indexOf(" with PID ");
      int endIdx = line.indexOf(" (", startIdx + 1);
      String pidString = line.substring(startIdx + " with PID ".length(), endIdx);
      this.pid = Integer.parseInt(pidString);
    }
  }

  public void waitForStartedState(final StartedProcess startedProcess) {
    while (stateRef.get() != STARTED) {
      if (!startedProcess.getProcess().isAlive()) {
        throw new RuntimeException("TMS exited without starting");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public int getPid() {
    if (pid == -1) {
      throw new IllegalStateException("TMS instance has not yet logged its PID");
    }
    return pid;
  }
}
