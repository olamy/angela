/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.client;

import org.terracotta.angela.KitResolver;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.topology.LicenseType;
import org.terracotta.angela.common.topology.PackageType;
import org.terracotta.angela.common.topology.Version;

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
