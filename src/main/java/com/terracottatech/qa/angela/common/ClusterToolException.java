package com.terracottatech.qa.angela.common;

/**
 * @author Aurelien Broszniowski
 */

public class ClusterToolException extends RuntimeException {
  private final String commandOutput;
  private final int exitValue;

  public ClusterToolException(final String message, final String commandOutput, final int exitValue) {
    super(message + " [exit value = " + exitValue + "]\n" + commandOutput);
    this.commandOutput = commandOutput;
    this.exitValue = exitValue;
  }

  public String getCommandOutput() {
    return commandOutput;
  }

  public int getExitValue() {
    return exitValue;
  }
}
