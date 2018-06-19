package com.terracottatech.qa.angela.client.remote.agent;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.util.LogOutputStream;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Aurelien Broszniowski
 */

class SshLogOutputStream extends LogOutputStream {

  private final String serverName;
  private final Session.Command cmd;
  private final AtomicBoolean started = new AtomicBoolean(false);

  SshLogOutputStream(String serverName, Session.Command cmd) {
    this.serverName = serverName;
    this.cmd = cmd;
  }

  @Override
  protected void processLine(final String line) {
    System.out.println("[ssh " + serverName + "] " + line);
    if (line.contains(Agent.AGENT_IS_READY_MARKER_LOG)) {
      started.set(true);
    }
  }

  public void waitForStartedState() {
    while (!started.get()) {
      if (!cmd.isOpen()) {
        throw new RuntimeException("agent refused to start");
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

}
