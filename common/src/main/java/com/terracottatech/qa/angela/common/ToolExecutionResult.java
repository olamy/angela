package com.terracottatech.qa.angela.common;

import java.util.List;

public class ToolExecutionResult {

  private int exitStatus;
  private List<String> output;

  public ToolExecutionResult(int exitStatus, List<String> output) {
    this.exitStatus = exitStatus;
    this.output = output;
  }

  public int getExitStatus() {
    return exitStatus;
  }

  public List<String> getOutput() {
    return output;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("rc=").append(exitStatus).append(" --- --- [start output] --- --- ---\n");
    for (String s : output) {
      sb.append(s).append("\n");
    }
    return sb.append("--- --- --- [ end output ] --- --- ---\n").toString();
  }
}
