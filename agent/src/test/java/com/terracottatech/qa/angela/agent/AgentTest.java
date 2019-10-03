package com.terracottatech.qa.angela.agent;

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
