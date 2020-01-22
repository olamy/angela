package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.KitResolver;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.net.URL;
import java.nio.file.Path;

/**
 * @author Aurelien Broszniowski
 */

public class DummyKitResolver extends KitResolver {
  @Override
  public String resolveLocalInstallerPath(Version version, LicenseType licenseType, PackageType packageType) {
    return null;
  }

  @Override
  public void createLocalInstallFromInstaller(Version version, PackageType packageType, License license, Path localInstallerPath, Path rootInstallationPath) {

  }

  @Override
  public Path resolveKitInstallationPath(Version version, PackageType packageType, Path localInstallerPath, Path rootInstallationPath) {
    return null;
  }

  @Override
  public URL[] resolveKitUrls(Version version, LicenseType licenseType, PackageType packageType) {
    return new URL[0];
  }

  @Override
  public boolean supports(LicenseType licenseType) {
    return true;
  }
}
