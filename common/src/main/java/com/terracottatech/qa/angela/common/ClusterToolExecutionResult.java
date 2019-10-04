package com.terracottatech.qa.angela.common;

import java.util.List;

public class ClusterToolExecutionResult extends ToolExecutionResult {
  public ClusterToolExecutionResult(int exitStatus, List<String> output) {
    super(exitStatus, output);
  }
}
