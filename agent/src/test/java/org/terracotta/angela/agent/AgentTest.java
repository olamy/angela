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

package org.terracotta.angela.agent;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class AgentTest {
  @Test
  public void testNoDebugOutput() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    PrintStream originalOut = System.out;
    try (PrintStream printStream = new PrintStream(baos)) {
      System.setOut(printStream);
      Agent.startNode().close();
    } finally {
      System.setOut(originalOut);
    }

    String out = new String(baos.toByteArray());

    assertThat(out, not(containsString("DEBUG")));
  }
}
