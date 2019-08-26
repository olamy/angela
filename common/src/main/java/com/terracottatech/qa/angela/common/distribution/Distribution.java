package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.util.Objects;

import static com.terracottatech.qa.angela.common.topology.LicenseType.EHCACHE_OS;
import static com.terracottatech.qa.angela.common.topology.LicenseType.GO;
import static com.terracottatech.qa.angela.common.topology.LicenseType.MAX;
import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static java.util.Objects.requireNonNull;

/**
 * @author Aurelien Broszniowski
 */

public class Distribution {

  private final Version version;
  private final PackageType packageType;
  private final LicenseType licenseType;

  public Distribution(Version version, PackageType packageType, LicenseType licenseType) {
    this.version = requireNonNull(version);
    this.packageType = requireNonNull(packageType);
    this.licenseType = validateLicenseType(version, licenseType);
  }

  private LicenseType validateLicenseType(Version version, LicenseType licenseType) {
    requireNonNull(licenseType);
    if (version.getMajor() == 4) {
      if (licenseType != GO && licenseType != MAX) {
        return throwException("Expected license of type '%s' or '%s for version: %s, but found: %s", GO, MAX, version, licenseType);
      }
    } else if (version.getMajor() == 3) {
      if (licenseType != EHCACHE_OS) {
        throwException("Expected license of type '%s' for version: %s, but found: %s", EHCACHE_OS, version, licenseType);
      }
    } else {
      if (licenseType != TERRACOTTA) {
        throwException("Expected license of type '%s' for version: %s, but found: %s", TERRACOTTA, version, licenseType);
      }
    }
    return licenseType;
  }

  private LicenseType throwException(String string, Object... args) {
    throw new IllegalArgumentException(String.format(string, args));
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

  public DistributionController createDistributionController() {
    if (version.getMajor() == 10 || version.getMajor() == 3) {
      return new Distribution102Controller(this);
    } else {
      if (version.getMinor() >= 3) {
        return new Distribution43Controller(this);
      }
    }
    throw new IllegalStateException("Cannot create a DistributionController with Version: " + version);
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
