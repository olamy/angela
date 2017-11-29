package com.terracottatech.qa.angela.common.util;

import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;

/**
 * @author Aurelien Broszniowski
 */

public class ServerLogOutputStream extends LogOutputStream {
  private final Pattern pattern = Pattern.compile("PID is (\\d*)");
  private final ServerSymbolicName serverSymbolicName;
  private final AtomicReference<TerracottaServerState> stateRef;
  private volatile int pid = -1;

  public ServerLogOutputStream(ServerSymbolicName serverSymbolicName, AtomicReference<TerracottaServerState> stateRef) {
    this.serverSymbolicName = serverSymbolicName;
    this.stateRef = stateRef;
  }

  @Override
  protected void processLine(final String line) {
    if (line.contains("WARN") || line.contains("ERROR")) {
      System.out.println("[" + serverSymbolicName.getSymbolicName() + "] " + line);
    }
    if (line.contains("Terracotta Server instance has started up as ACTIVE")) {
      stateRef.set(STARTED_AS_ACTIVE);
    } else if (line.contains("Moved to State[ PASSIVE-STANDBY ]")) {
      stateRef.set(STARTED_AS_PASSIVE);
    } else if (line.contains("PID is")) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        this.pid = Integer.parseInt(matcher.group(1));
      }
    }
  }

  public void waitForStartedState(final StartedProcess startedProcess) {
    while (stateRef.get() != STARTED_AS_ACTIVE && stateRef.get() != STARTED_AS_PASSIVE) {
      if (!startedProcess.getProcess().isAlive()) {
        throw new RuntimeException("TCServer exited without reaching ACTIVE or PASSIVE state");
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
      throw new IllegalStateException("Terracotta Server instance has not yet logged its PID");
    }
    return pid;
  }
}
