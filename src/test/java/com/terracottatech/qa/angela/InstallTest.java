package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.TsaControl;
import org.junit.Test;

import com.terracottatech.qa.angela.agent.kit.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.ClusterToolConfig;
import com.terracottatech.qa.angela.common.tcconfig.LicenseConfig;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {

  @Test
  public void testRemoteInstall() throws InterruptedException {
    TsaControl control = new TsaControl();

    TcConfig tcConfig = new TcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-a.xml");


    Topology topology = new Topology("1", Distribution.distribution(version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB), tcConfig);

//    Topology topology = new Topology("1", Distribution.distribution("/my/path", version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB), tcConfig);


    ClusterToolConfig clusterToolConfig = new ClusterToolConfig("localhost", new LicenseConfig("/terracotta/10/TerracottaDB101_license.xml"));

    control.withTopology(topology)
        .withClusterToolConfig(clusterToolConfig)
        .init();

    control.startAll();

    System.out.println("---> Wait for 5 sec");
    Thread.sleep(5000);
    System.out.println("---> Stop");

    control.stopAll();

    control.close();
  }

  @Test
  public void testInit() throws Exception {
    TsaControl tsaControl1 = new TsaControl();
    tsaControl1.init();

    TsaControl tsaControl2 = new TsaControl();
    tsaControl2.init();
    TsaControl tsaControl3 = new TsaControl();
    tsaControl3.init();

    tsaControl1.close();
    tsaControl2.close();
    tsaControl3.close();
  }
}
