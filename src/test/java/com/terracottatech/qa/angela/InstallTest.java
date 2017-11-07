package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.tcconfig.TcConfig;
import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.PackageType;
import com.terracottatech.qa.angela.topology.Topology;

import static com.terracottatech.qa.angela.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class InstallTest {

  @Test
  public void testRemoteInstall() {
    TsaControl control = new TsaControl();

    TcConfig tcConfig = new TcConfig("/terracotta/10/tc-config-a.xml");

    Topology topology = new Topology("1", version("10.1.0-SNAPSHOT", PackageType.KIT, LicenseType.TC_DB), tcConfig);
    control.withTopology(topology).init();



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
