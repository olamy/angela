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

package org.terracotta.angela.common.metrics;

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
