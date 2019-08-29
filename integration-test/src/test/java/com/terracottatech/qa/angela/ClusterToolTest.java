package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ClusterTool;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_10X_A;
import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_4X_A;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Yakov Feldman
 */
public class ClusterToolTest {
  private final static Logger logger = LoggerFactory.getLogger(ClusterToolTest.class);

  @Test
  public void testExecute() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
                    tcConfig(version(Versions.TERRACOTTA_VERSION), TC_CONFIG_10X_A)
                )
            ).license(LicenseType.TERRACOTTA.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("ClusterToolTest::testExecute", configContext)) {
      Tsa tsa = factory.tsa().startAll();
      ClusterTool clusterTool = tsa.clusterTool(tsa.getActive());

      ClusterToolExecutionResult execution = clusterTool.executeCommand(
          "-v", "configure",
          "-l", tsa.licensePath(tsa.getActive()),
          "-n", "mycluster",
          "-s", tsa.getActive().getHostname() + ":" + tsa.getActive().getPorts().getTsaPort()
      );

      int code = execution.getExitStatus();
      assertThat(code, equalTo(0)); // 0 exit code means successful execution
    }
  }

  @Test
  public void testFailsOn4x() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa
            .topology(
                new Topology(
                    distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
                    tcConfig(version(Versions.TERRACOTTA_VERSION_4X), TC_CONFIG_4X_A)
                )
            ).license(LicenseType.MAX.defaultLicense())
        );

    try (ClusterFactory factory = new ClusterFactory("ClusterToolTest::testFailsOn4x", configContext)) {
      Tsa tsa = factory.tsa().startAll();
      ClusterTool clusterTool = tsa.clusterTool(tsa.getActive());

      try {
        clusterTool.executeCommand(
            "-v", "configure",
            "-l", tsa.licensePath(tsa.getActive()),
            "-n", "mycluster",
            "-s", tsa.getActive().getHostname() + ":" + tsa.getActive().getPorts().getTsaPort()
        );
        fail("cluster tool should fail on 4.x");
      } catch (Exception e) {
        // expected
      }
    }
  }
}