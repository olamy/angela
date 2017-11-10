package com.terracottatech.qa.angela.kit.distribution;

import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.PackageType;
import com.terracottatech.qa.angela.topology.Version;

/**
 * @author Aurelien Broszniowski
 */

public abstract class DistributionController {

  private final Version version;
  private final PackageType packageType;
  private final LicenseType licenseType;

  public DistributionController(final Version version, final PackageType packageType, final LicenseType licenseType) {
    this.version = version;
    this.packageType = packageType;
    this.licenseType = licenseType;
  }

  public Version getVersion() {
    return version;
  }

  public String getVersion(boolean showSnapshot) {
    return version.getVersion(showSnapshot);
  }

  public PackageType getPackageType() {
    return packageType;
  }

  public LicenseType getLicenseType() {
    return licenseType;
  }

  public abstract void start();

  public abstract void stop();

}
