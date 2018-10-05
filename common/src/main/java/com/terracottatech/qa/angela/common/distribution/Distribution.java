package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution {

  private final Version version;
  private final PackageType packageType;
  private final LicenseType licenseType;

  public Distribution(Version version, PackageType packageType, LicenseType licenseType) {
    this.version = Objects.requireNonNull(version);
    this.packageType = Objects.requireNonNull(packageType);
    this.licenseType = Objects.requireNonNull(licenseType);
  }

  public static Distribution distribution(Version version, PackageType packageType, LicenseType licenseType) {
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Distribution that = (Distribution) o;
    return Objects.equals(version, that.version) &&
        packageType == that.packageType &&
        licenseType == that.licenseType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, packageType, licenseType);
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
