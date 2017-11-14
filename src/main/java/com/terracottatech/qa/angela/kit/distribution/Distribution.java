package com.terracottatech.qa.angela.kit.distribution;

import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.PackageType;
import com.terracottatech.qa.angela.topology.Version;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution {

  private final Version version;
  private final PackageType packageType;
  private final LicenseType licenseType;

  public Distribution(final Version version, final PackageType packageType, final LicenseType licenseType) {
    this.version = version;
    this.packageType = packageType;
    this.licenseType = licenseType;
  }

  public static Distribution distribution(Version version, final PackageType packageType, final LicenseType licenseType) {
    return new Distribution(version, packageType, licenseType);
  }

  public Version getVersion() {
    return version;
  }

  public PackageType getPackageType() {
    return packageType;
  }

  public LicenseType getLicenseType() {
    return licenseType;
  }

  @Override
  public String toString() {
    return "Distribution{" +
           "version=" + version +
           ", packageType=" + packageType +
           ", licenseType=" + licenseType +
           '}';
  }
}
