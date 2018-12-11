package com.terracottatech.qa.angela.common.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MonitoringCommand {

  private final List<String> command;

  public MonitoringCommand(String... cmdArgs) {
    this(Arrays.asList(cmdArgs));
  }

  public MonitoringCommand(List<String> cmdArgs) {
    this.command = new ArrayList<>(cmdArgs);
  }

  public String getCommandName() {
    return command.get(0);
  }

  public List<String> getCommand() {
    return Collections.unmodifiableList(command);
  }

}
