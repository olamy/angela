package com.terracottatech.qa.angela.common;

import java.util.List;

public class ConfigToolExecutionResult extends ToolExecutionResult {
  public ConfigToolExecutionResult(int exitStatus, List<String> output) {
    super(exitStatus, output);
  }
}