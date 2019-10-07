package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.ToolExecutionResult;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_AP;
import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_4X_AP;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.JCMD;
import static com.terracottatech.qa.angela.client.config.TsaConfigurationContext.TerracottaCommandLineEnvironmentKeys.SERVER_START_PREFIX;
import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.LicenseType.MAX;
import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.qa.angela.test.Versions.TERRACOTTA_VERSION;
import static com.terracottatech.qa.angela.test.Versions.TERRACOTTA_VERSION_4X;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class JcmdTest {

  // this test is bound to the Oracle JDK as Zulu's jcmd output is slightly different
  private static final TerracottaCommandLineEnvironment ORACLE_JVM_WITH_FLIGHT_RECORDER = TerracottaCommandLineEnvironment.DEFAULT.withJavaVendor("Oracle Corporation").withJavaOpts(Arrays.asList("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder"));

  @Test
  public void testControlServerFlightRecorderWithJcmd_10x() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION), KIT, TERRACOTTA), tcConfig(version(TERRACOTTA_VERSION), TC_CONFIG_10X_AP)))
            .license(TERRACOTTA.defaultLicense())
            .terracottaCommandLineEnvironment(SERVER_START_PREFIX + "Server1", ORACLE_JVM_WITH_FLIGHT_RECORDER)
            .terracottaCommandLineEnvironment(SERVER_START_PREFIX + "Server2", ORACLE_JVM_WITH_FLIGHT_RECORDER)
            .terracottaCommandLineEnvironment(JCMD, ORACLE_JVM_WITH_FLIGHT_RECORDER)
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testControlServerFlightRecorderWithJcmd_10x", config)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      for (TerracottaServer server : tsa.getTsaConfigurationContext().getTopology().getServers()) {
        ToolExecutionResult executionResult = tsa.jcmd(server).executeCommand("JFR.start", "duration=60s", "filename=flight_recording.jfr");
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("Started recording"));

        executionResult = tsa.jcmd(server).executeCommand("JFR.check");
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("recording="));
        assertThat(executionResult.getOutput().get(1), containsString("name=\"flight_recording.jfr\""));
        assertThat(executionResult.getOutput().get(1), containsString("(running)"));
        String recordingId = findRecordingId(executionResult.getOutput().get(1));

        executionResult = tsa.jcmd(server).executeCommand("JFR.stop", "recording=" + recordingId);
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("Stopped recording " + recordingId));
      }
    }
  }

  @Test
  public void testControlServerFlightRecorderWithJcmd_4x() throws Exception {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(new Topology(distribution(version(TERRACOTTA_VERSION_4X), KIT, MAX), tcConfig(version(TERRACOTTA_VERSION_4X), TC_CONFIG_4X_AP)))
            .license(MAX.defaultLicense())
            .terracottaCommandLineEnvironment(SERVER_START_PREFIX + "Server1", ORACLE_JVM_WITH_FLIGHT_RECORDER)
            .terracottaCommandLineEnvironment(SERVER_START_PREFIX + "Server2", ORACLE_JVM_WITH_FLIGHT_RECORDER)
            .terracottaCommandLineEnvironment(JCMD, ORACLE_JVM_WITH_FLIGHT_RECORDER)
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testControlServerFlightRecorderWithJcmd_4x", config)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      for (TerracottaServer server : tsa.getTsaConfigurationContext().getTopology().getServers()) {
        ToolExecutionResult executionResult = tsa.jcmd(server).executeCommand("JFR.start", "duration=60s", "filename=flight_recording.jfr");
        assertThat("Invalid exit status: " + executionResult.toString(), executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("Started recording"));

        executionResult = tsa.jcmd(server).executeCommand("JFR.check");
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("recording="));
        assertThat(executionResult.getOutput().get(1), containsString("name=\"flight_recording.jfr\""));
        assertThat(executionResult.getOutput().get(1), containsString("(running)"));
        String recordingId = findRecordingId(executionResult.getOutput().get(1));

        executionResult = tsa.jcmd(server).executeCommand("JFR.stop", "recording=" + recordingId);
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("Stopped recording " + recordingId));
      }
    }
  }

  @Test
  public void testControlClientFlightRecorderWithJcmd() throws IOException {
    ConfigurationContext config = CustomConfigurationContext.customConfigurationContext()
        .clientArray(client -> client.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(2, "localhost")))
            .terracottaCommandLineEnvironment(ORACLE_JVM_WITH_FLIGHT_RECORDER)
        );

    try (ClusterFactory factory = new ClusterFactory("InstallTest::testControlClientFlightRecorderWithJcmd", config)) {
      ClientArray clientArray = factory.clientArray();
      Collection<Client> clients = clientArray.getClients();

      for (Client client : clients) {
        ToolExecutionResult executionResult = clientArray.jcmd(client).executeCommand("JFR.start", "duration=60s", "filename=flight_recording.jfr");
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("Started recording"));

        executionResult = clientArray.jcmd(client).executeCommand("JFR.check");
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("recording="));
        assertThat(executionResult.getOutput().get(1), containsString("name=\"flight_recording.jfr\""));
        assertThat(executionResult.getOutput().get(1), containsString("(running)"));
        String recordingId = findRecordingId(executionResult.getOutput().get(1));

        executionResult = clientArray.jcmd(client).executeCommand("JFR.stop", "recording=" + recordingId);
        assertThat(executionResult.getExitStatus(), is(0));
        assertThat(executionResult.getOutput().get(1), containsString("Stopped recording " + recordingId));
      }
    }
  }

  private static String findRecordingId(String line) {
    Pattern pattern = Pattern.compile(".* recording=(\\d+) .*");
    Matcher matcher = pattern.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    } else {
      throw new AssertionError("Unable to find record ID in string [" + line + "]");
    }
  }

}