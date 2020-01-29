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

package org.terracotta.angela.common.tcconfig.holders;

import org.junit.Test;
import org.terracotta.angela.common.tcconfig.holders.TcConfig10Holder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    Path kitDir = Paths.get("mylocation");
    tcConfig10Holder.updateLogsLocation(kitDir.toFile(), 2);
    assertThat(tcConfig10Holder.tcConfigContent, is(equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tc-config xmlns=\"http://www.terracotta.org/config\"><plugins/><servers><server><logs>" + kitDir.toAbsolutePath().resolve("logs-2-1") + "</logs></server></servers></tc-config>")));
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
    Path kitDir = Paths.get("mylocation");
    tcConfig10Holder.updateLogsLocation(kitDir.toFile(), 0);
    assertThat(tcConfig10Holder.tcConfigContent, is(equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tc-config xmlns=\"http://www.terracotta.org/config\"><plugins/><servers><server><logs>" + kitDir.toAbsolutePath().resolve("logs-0-1") + "</logs></server></servers></tc-config>")));
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
    Path kitDir = Paths.get("target","mylocation");
    tcConfig10Holder.updateLogsLocation(kitDir.toFile(), 0);
    assertThat(tcConfig10Holder.tcConfigContent, is(equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tc-config xmlns=\"http://www.terracotta.org/config\"><plugins/><servers><server><logs>" + kitDir.toAbsolutePath().resolve("logs-0-1") + "</logs></server><server><logs>" + kitDir.toAbsolutePath().resolve("logs-0-2") + "</logs></server></servers></tc-config>")));
  }

}
