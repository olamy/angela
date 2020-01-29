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

package org.terracotta.angela.ehc3;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terracotta.angela.KitResolver;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.topology.LicenseType;
import org.terracotta.angela.common.topology.PackageType;
import org.terracotta.angela.common.topology.Version;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.terracotta.angela.common.topology.PackageType.KIT;

/**
 * @author Aurelien Broszniowski
 */

public class Ehc3KitResolver extends KitResolver {

  private static final Logger logger = LoggerFactory.getLogger(Ehc3KitResolver.class);

  @Override
  public String resolveLocalInstallerPath(Version version, LicenseType licenseType, PackageType packageType) {
    if (packageType == KIT) {
      return "ehcache-clustered-" + version.getVersion(true) + "-kit.zip";
    }
    throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName() + " in the Open source version.");
  }

  @Override
  public void createLocalInstallFromInstaller(Version version, PackageType packageType, License license, Path localInstallerPath, Path rootInstallationPath) {
    if (packageType == KIT) {
      extract(localInstallerPath, rootInstallationPath);
    } else {
      throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
    }
  }

  private void extract(Path kitInstaller, Path kitDest) {
    try {
      extractZip(kitInstaller, kitDest);
    } catch (IOException ioe) {
      throw new UncheckedIOException("Error when extracting installer package", ioe);
    }
    logger.info("kit installation path: {}", kitDest.toAbsolutePath());
  }

  // TODO : duplicate
  private void extractZip(Path kitInstaller, Path kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(kitInstaller)))) {
      extractArchive(archiveIs, kitDest);
    }
    cleanupPermissions(kitDest);
  }

  // TODO : duplicate
  private void extractArchive(ArchiveInputStream archiveIs, Path pathOutput) throws IOException {
    while (true) {
      ArchiveEntry archiveEntry = archiveIs.getNextEntry();
      if (archiveEntry == null) {
        break;
      }

      Path pathEntryOutput = pathOutput.resolve(archiveEntry.getName());
      if (!archiveEntry.isDirectory()) {
        Path parentPath = pathEntryOutput.getParent();
        if (!Files.isDirectory(parentPath)) {
          Files.createDirectories(parentPath);
        }
        Files.copy(archiveIs, pathEntryOutput);
      }
    }
  }

  // TODO : duplicate
  private void cleanupPermissions(Path dest) {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return;
    }

    try (Stream<Path> walk = Files.walk(dest)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".sh") || name.endsWith("tms.jar");
          })
          .forEach(path -> {
            try {
              Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(path));
              perms.addAll(EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
              Files.setPosixFilePermissions(path, perms);
            } catch (IOException ioe) {
              throw new UncheckedIOException(ioe);
            }
          });
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  @Override
  public Path resolveKitInstallationPath(Version version, PackageType packageType, Path localInstallerPath, Path rootInstallationPath) {
    return rootInstallationPath.resolve(getDirFromArchive(packageType, localInstallerPath));
  }

  private String getDirFromArchive(PackageType packageType, Path localInstaller) {
    if (packageType == KIT) {
    return getParentDirFromZip(localInstaller);
    }
    throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
  }

  private String getParentDirFromZip(Path localInstaller) {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  @Override
  public URL[] resolveKitUrls(Version version, LicenseType licenseType, PackageType packageType) {
    try {
      if (packageType == KIT) {
        String realVersion = version.getVersion(true);
        StringBuilder sb = new StringBuilder("https://oss.sonatype.org/service/local/artifact/maven/redirect?")
            .append("g=org.ehcache&")
            .append("a=ehcache-clustered&")
            .append("c=kit&")
            .append(realVersion.contains("SNAPSHOT") ? "r=snapshots&" : "r=releases&")
            .append("v=").append(realVersion).append("&")
            .append("e=zip");

        URL kitUrl = new URL(sb.toString());
        URL md5Url = new URL(sb.toString() + ".md5");
        return new URL[] { kitUrl, md5Url };
      } else {
        throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Can not resolve the url for the distribution package: " + packageType + ", " + licenseType + ", " + version, e);
    }
  }

  @Override
  public boolean supports(LicenseType licenseType) {
    return licenseType == LicenseType.EHCACHE_OS;
  }
}
