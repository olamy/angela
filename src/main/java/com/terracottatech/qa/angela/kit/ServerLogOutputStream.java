package com.terracottatech.qa.angela.kit;

import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.util.concurrent.atomic.AtomicReference;

import static com.terracottatech.qa.angela.kit.TerracottaServerInstance.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.kit.TerracottaServerInstance.TerracottaServerState.STARTED_AS_PASSIVE;

/**
 * @author Aurelien Broszniowski
 */

public class ServerLogOutputStream extends LogOutputStream {
  private AtomicReference<TerracottaServerInstance.TerracottaServerState> stateRef = new AtomicReference<>();

  public ServerLogOutputStream() {
  }

  @Override
  protected void processLine(final String line) {
    System.out.println(line);
    if (line.contains("Terracotta Server instance has started up as ACTIVE")) {
      stateRef.set(STARTED_AS_ACTIVE);
    } else if (line.contains("Moved to State[ PASSIVE-STANDBY ]")) {
      stateRef.set(STARTED_AS_PASSIVE);
    }
  }

  public TerracottaServerInstance.TerracottaServerState waitForStartedState(final StartedProcess startedProcess) {
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
    return stateRef.get();
  }
}
