package com.terracottatech.qa.angela.common.util;

import com.terracottatech.qa.angela.common.TerracottaManagementServerState;
import org.zeroturnaround.exec.StartedProcess;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.terracottatech.qa.angela.common.TerracottaManagementServerState.STARTED;

/**
 * @author Anthony Dahanne
 */

public class TerracottaManagementServerLogOutputStream extends LogOutputStream {
  private final Pattern pattern = Pattern.compile("PID=(\\d*)");
  private final AtomicReference<TerracottaManagementServerState> stateRef;
  private volatile int pid = -1;

  public TerracottaManagementServerLogOutputStream(AtomicReference<TerracottaManagementServerState> stateRef) {
    this.stateRef = stateRef;
  }

  @Override
  protected void processLine(final String line) {
    if (line.contains("WARN") || line.contains("ERROR") || Boolean.getBoolean("angela.tms.log.full")) {
      System.out.println("[TMS] " + line);
    }
    if (line.contains("Started TmsApplication")) {
      stateRef.set(STARTED);
    } else if (line.contains("PID=")) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        this.pid = Integer.parseInt(matcher.group(1));
      }
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
