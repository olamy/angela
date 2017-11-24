package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution {

  private final Version version;
  private final PackageType packageType;
  private final LicenseType licenseType;
  private final File localPath;

  public Distribution(final Version version, final PackageType packageType, final LicenseType licenseType) {
    this(null, version, packageType, licenseType);
  }

  public Distribution(final File localPath, final Version version, final PackageType packageType, final LicenseType licenseType) {
    this.localPath = localPath;
    this.version = version;
    this.packageType = packageType;
    this.licenseType = licenseType;
  }

  public static Distribution distribution(Version version, final PackageType packageType, final LicenseType licenseType) {
    return new Distribution(version, packageType, licenseType);
  }

  public static Distribution distribution(File path, Version version, final PackageType packageType, final LicenseType licenseType) {
    return new Distribution(path, version, packageType, licenseType);
  }

  public File getLocalPath() {
    return localPath;
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
