/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.client.remote.agent;

import org.terracotta.angela.agent.Agent;
import org.terracotta.angela.common.util.ExternalLoggers;
import org.terracotta.angela.common.util.LogOutputStream;
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
  protected void processLine(String line) {
    ExternalLoggers.sshLogger.info("[{}] {}", serverName, line);
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
