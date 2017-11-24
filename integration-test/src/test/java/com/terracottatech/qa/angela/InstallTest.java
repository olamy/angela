package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.client.TsaControl;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.io.File;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {

  @Test
  public void testLocallInstall() {
    TcConfig tcConfig = new TcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-a.xml");
    Topology topology = new Topology("TcDBTest::testConnection", distribution(new File("/work/terracotta/irepo/abroszni/terracotta-enterprise/distribution/kit/target/voltronKit/terracotta-5.4.0-pre8"), version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB), tcConfig);
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (TsaControl control = new TsaControl(topology, license)) {
      control.installAll();
      control.startAll();
      control.licenseAll();



      control.stopAll();
    }
  }

}
