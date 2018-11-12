package com.terracottatech.qa.angela.common.tcconfig.holders;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Aurelien Broszniowski
 */

public class TcConfigHolderTest {

  @Test
  public void testServerLogsStripeUpdate() {
    String tcConfigAsString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                              "<tc-config xmlns=\"http://www.terracotta.org/config\">" +
                              "<plugins>" +
                              "</plugins>" +
                              "<servers>" +
                              "<server>" +
                              "</server>" +
                              "</servers>" +
                              "</tc-config>";
    final TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(new ByteArrayInputStream(tcConfigAsString.getBytes(StandardCharsets.UTF_8)));
    tcConfig10Holder.updateLogsLocation(new File("/mylocation"), 2);
    assertThat(tcConfig10Holder.tcConfigContent, is(equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tc-config xmlns=\"http://www.terracotta.org/config\"><plugins/><servers><server><logs>/mylocation/logs-2-1</logs></server></servers></tc-config>")));
  }

 @Test
  public void test1ServerLogsUpdate() {
    String tcConfigAsString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                              "<tc-config xmlns=\"http://www.terracotta.org/config\">" +
                              "<plugins>" +
                              "</plugins>" +
                              "<servers>" +
                              "<server>" +
                              "</server>" +
                              "</servers>" +
                              "</tc-config>";
    final TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(new ByteArrayInputStream(tcConfigAsString.getBytes(StandardCharsets.UTF_8)));
    tcConfig10Holder.updateLogsLocation(new File("/mylocation"), 0);
    assertThat(tcConfig10Holder.tcConfigContent, is(equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tc-config xmlns=\"http://www.terracotta.org/config\"><plugins/><servers><server><logs>/mylocation/logs-0-1</logs></server></servers></tc-config>")));
  }

  @Test
  public void test2ServersLogsUpdate() {
    String tcConfigAsString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                              "<tc-config xmlns=\"http://www.terracotta.org/config\">" +
                              "<plugins>" +
                              "</plugins>" +
                              "<servers>" +
                              "<server>" +
                              "</server>" +
                              "<server>" +
                              "</server>" +
                              "</servers>" +
                              "</tc-config>";
    final TcConfig10Holder tcConfig10Holder = new TcConfig10Holder(new ByteArrayInputStream(tcConfigAsString.getBytes(StandardCharsets.UTF_8)));
    tcConfig10Holder.updateLogsLocation(new File("/mylocation"), 0);
    assertThat(tcConfig10Holder.tcConfigContent, is(equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tc-config xmlns=\"http://www.terracotta.org/config\"><plugins/><servers><server><logs>/mylocation/logs-0-1</logs></server><server><logs>/mylocation/logs-0-2</logs></server></servers></tc-config>")));
  }

}
