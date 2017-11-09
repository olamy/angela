package com.terracottatech.qa.angela.kit.distribution;

import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.PackageType;
import com.terracottatech.qa.angela.topology.Version;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution {

  public static DistributionController distribution(Version version, final PackageType packageType, final LicenseType licenseType) {
    if (version.getMajor() == 10) {
      if (version.getMinor() == 0) {
        return new Distribution100Controller(version, packageType, licenseType);
      } else if (version.getMinor() > 0) {
        return new Distribution102Controller(version, packageType, licenseType);
      }
    }
    throw new IllegalArgumentException("Version not supported");
  }

}
