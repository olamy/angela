package com.terracottatech.qa.angela.kit.distribution;

import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.PackageType;
import com.terracottatech.qa.angela.topology.Version;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution100Controller extends DistributionController {

  public Distribution100Controller(final Version version, final PackageType packageType, final LicenseType licenseType) {
    super(version, packageType, licenseType);
  }

  @Override
  public void start() {
    System.out.println("start 10.0");
  }

  @Override
  public void stop() {
    System.out.println("stop 10.0");
  }
}
