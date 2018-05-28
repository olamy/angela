package com.terracottatech.qa.angela.common.distribution;

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
