package com.terracottatech.qa.angela;

import org.junit.Test;

import com.terracottatech.qa.angela.client.TsaControl;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.util.concurrent.ExecutionException;

import static com.terracottatech.qa.angela.common.topology.Version.version;

/**
 * @author Aurelien Broszniowski
 */

public class MultistripesTest {

  @Test
  public void test2Stripes() throws InterruptedException, ExecutionException {

    TcConfig tcConfig1 = new TcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-multistripes1.xml");
    TcConfig tcConfig2 = new TcConfig(version("10.1.0-SNAPSHOT"), "/terracotta/10/tc-config-multistripes2.xml");

    Topology topology = new Topology("1", Distribution.distribution(version("10.1.0-SNAPSHOT"), PackageType.KIT, LicenseType.TC_DB), tcConfig1, tcConfig2);
    License license = new License("/terracotta/10/TerracottaDB101_license.xml");

    try (TsaControl control = new TsaControl(topology, license)) {
      control.installAll();
      control.startAll();
      control.licenseAll();

      System.out.println("---> Wait for 3 sec");
      Thread.sleep(3000);


      System.out.println("---> Stop");
      control.stopAll();

    }
  }
}
