package com.terracottatech.qa.angela.common.util;

import com.terracottatech.qa.angela.common.TerracottaServerState;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_ACTIVE;
import static com.terracottatech.qa.angela.common.TerracottaServerState.STARTED_AS_PASSIVE;

/**
 * @author Aurelien Broszniowski
 */

public class ServerLogOutputStream extends LogOutputStream {
  private AtomicReference<TerracottaServerState> stateRef = new AtomicReference<>();
  private AtomicInteger pid;

  public ServerLogOutputStream(final AtomicInteger pid) {
    this.pid = pid;
  }

  @Override
  protected void processLine(final String line) {
    System.out.println(line);
    if (line.contains("Terracotta Server instance has started up as ACTIVE")) {
      stateRef.set(STARTED_AS_ACTIVE);
    } else if (line.contains("Moved to State[ PASSIVE-STANDBY ]")) {
      stateRef.set(STARTED_AS_PASSIVE);
    } else if (line.contains("PID is")) {
      Pattern pattern = Pattern.compile("PID is (\\d*)");
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        this.pid.set(Integer.parseInt(matcher.group(1)));
      }
    }
  }

  public TerracottaServerState waitForStartedState(final StartedProcess startedProcess) {
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

  public void stop() {

  }
}
