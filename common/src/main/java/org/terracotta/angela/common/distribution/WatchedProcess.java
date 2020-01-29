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

package org.terracotta.angela.common.distribution;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidUtil;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;


class WatchedProcess<S extends Enum<S>> {

  private final StartedProcess startedProcess;
  private final int pid;

  public WatchedProcess(ProcessExecutor processExecutor, final AtomicReference<S> stateRef, final S deadState) {
    try {
      this.startedProcess = processExecutor.start();
    } catch (IOException e) {
      throw new RuntimeException("Cannot start process " + processExecutor.getCommand(), e);
    }
    this.pid = PidUtil.getPid(startedProcess.getProcess());

    Thread watcherThread = new Thread(() -> {
      try {
        startedProcess.getFuture().get();
        stateRef.set(deadState);
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    });
    watcherThread.setDaemon(true);
    watcherThread.setName("ProcessWatcher on PID#" + pid);
    watcherThread.start();
  }

  public boolean isAlive() {
    return startedProcess.getProcess().isAlive();
  }

  public int getPid() {
    return pid;
  }
}
