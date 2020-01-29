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

package org.terracotta.angela.common;

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
