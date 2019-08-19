package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.ClusterTool;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import com.terracottatech.tools.clustertool.result.ClusterToolCommandResults;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

/**
 * @author Yakov Feldman
 */

public class ClusterToolTest {

  private final static Logger logger = LoggerFactory.getLogger(ClusterToolTest.class);

  @Test
  public void testExecute() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(
                Distribution.distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
                tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/Terracotta101.xml")))
        );

    try (ClusterFactory factory = new ClusterFactory("ClusterToolTest::testExecute", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll();

      ClusterTool clusterTool = tsa.clusterTool(tsa.getActive());

      ClusterToolExecutionResult execution = clusterTool.executeCommand(
          "-v", "configure",
          "-l", tsa.licensePath(tsa.getActive()),
          "-n", "mycluster",
          "-s", tsa.getActive().getHostname() + ":" + tsa.getActive().getPorts().getTsaPort());

      int code = execution.getExitStatus();

      System.out.println(execution);

      assertThat(
          Arrays.stream(ClusterToolCommandResults.StatusCode.values()).filter(status -> status.getCode() == code).findFirst().get().toString(),
          is(ClusterToolCommandResults.StatusCode.SUCCESS.toString())
      );

    }

    logger.info("---> Stop");
  }

  @Test
  public void testFailsOn4x() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(
            Distribution.distribution(version(Versions.TERRACOTTA_VERSION_4X), PackageType.KIT, LicenseType.MAX),
            tcConfig(version(Versions.TERRACOTTA_VERSION_4X), getClass().getResource("/terracotta/4/tc-config-a.xml"))))
            .license(new License(getClass().getResource("/terracotta/4/terracotta-license.key")))
        );

    try (ClusterFactory factory = new ClusterFactory("ClusterToolTest::testFailsOn4x", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll();

      ClusterTool clusterTool = tsa.clusterTool(tsa.getActive());

      try {
        clusterTool.executeCommand(
            "-v", "configure",
            "-l", tsa.licensePath(tsa.getActive()),
            "-n", "mycluster",
            "-s", tsa.getActive().getHostname() + ":" + tsa.getActive().getPorts().getTsaPort());
        fail("cluster tool should fail on 4.x");
      } catch (Exception e) {
        // expected
      }
    }
  }

}