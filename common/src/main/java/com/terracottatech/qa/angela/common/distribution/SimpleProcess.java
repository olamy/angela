package com.terracottatech.qa.angela.common.distribution;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import java.io.IOException;

/**
 * @author Aurelien Broszniowski
 */

public class SimpleProcess {

  private final StartedProcess startedProcess;
  private final Integer pid;

  public SimpleProcess(ProcessExecutor processExecutor) {
    try {
      this.startedProcess = processExecutor.start();
    } catch (IOException e) {
      throw new RuntimeException("Cannot start process " + processExecutor.getCommand(), e);
    }
    this.pid = PidUtil.getPid(startedProcess.getProcess());
  }

  public SimpleProcess() {
    this.startedProcess = null;
    this.pid = null;
  }

  public boolean isAlive() {
    return startedProcess.getProcess().isAlive();
  }

  public int getPid() {
    return pid;
  }

}
