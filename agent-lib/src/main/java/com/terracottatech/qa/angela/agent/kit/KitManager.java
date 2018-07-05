package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.agent.Agent.ROOT_DIR_SYSPROP_NAME;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * @author Aurelien Broszniowski
 */

public abstract class KitManager {

  private static final Logger logger = LoggerFactory.getLogger(KitManager.class);

  protected Distribution distribution;
  protected CompressionUtils compressionUtils = new CompressionUtils();

  protected final String rootInstallationPath;  // the work directory where installs are stored for caching
  protected File kitInstallationPath; // the extracted install to be used as a cached install

  public KitManager(final Distribution distribution) {
    this.distribution = distribution;

    String localWorkRootDir;
    final String dir = System.getProperty(ROOT_DIR_SYSPROP_NAME);
    if (dir == null || dir.isEmpty()) {
      localWorkRootDir = new File("/data/angela").getAbsolutePath();
    } else if (dir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the ROOT_DIR. Please use a fixed one.");
    } else {
      localWorkRootDir = dir;
    }

    this.rootInstallationPath = localWorkRootDir + File.separator + (distribution.getPackageType() == SAG_INSTALLER ? "sag" : "kits")
                                + File.separator + distribution.getVersion().getVersion(false);
  }

  /**
   * resolve installer file location
   *
   * @param offline
   * @return location of the installer archive file
   */
  protected boolean isValidLocalInstallerFilePath(final boolean offline, File localInstallerFilename) {
    if (!localInstallerFilename.isFile()) {
      logger.debug("Kit {} is not an existing file", localInstallerFilename.getAbsolutePath());
      return false;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstallerFilename.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      logger.debug("Our version is a snapshot, is older than 24h and we are not offline so we are deleting it to produce a reload.");
      FileUtils.deleteQuietly(localInstallerFilename);
      return false;
    }
    return true;
  }

  /**
   * Resolve install path that will be used to create a working copy
   * <p>
   * This is the directory containing the exploded terracotta install, that will be copied to give a
   * single instance working installation
   * <p>
   * e.g. : /data/tsamanager/kits/10.1.0/terracotta-db-10.1.0-SNAPSHOT
   *
   * @param offline
   * @param installationPath
   * @return location of the install to be used to create the working install
   */
  protected boolean isValidKitInstallationPath(final boolean offline, final File installationPath) {
    if (!installationPath.isDirectory()) {
      logger.debug("Install is not available.");
      return false;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - installationPath.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      try {
        logger.debug("Version is snapshot and older than 24h and mode is online, so we reinstall it.");
        FileUtils.deleteDirectory(installationPath);
      } catch (IOException e) {
        return false;
      }
      return false;
    }
    return true;
  }

  String resolveLocalInstallerFilename() {
    logger.debug("Resolving the local installer name");
    StringBuilder sb = new StringBuilder(this.rootInstallationPath);
    sb.append(File.separator);
    Version version = distribution.getVersion();
    if (distribution.getPackageType() == KIT) {

      if (version.getMajor() == 4) {
        sb.append("bigmemory-");
        if (distribution.getLicenseType() == LicenseType.GO) {
          sb.append("go-");
        } else if (distribution.getLicenseType() == LicenseType.MAX) {
          sb.append("max-");
        }
        sb.append(version.getVersion(true)).append(".tar.gz");
        logger.debug("Kit name: {}", sb.toString());
        return sb.toString();
      } else if (version.getMajor() == 5) {
        if (distribution.getLicenseType() == LicenseType.TC_EHC) {
          sb.append("uberkit-").append(version.getVersion(true)).append("-kit.zip");
        } else if (distribution.getLicenseType() == LicenseType.OS) {
          sb.append("ehcache-clustered-").append(version.getRealVersion(true, false)).append("-kit.zip");
        }
        logger.debug("Kit name: {}", sb.toString());
        return sb.toString();
      } else if (version.getMajor() == 10) {
        if (distribution.getLicenseType() == LicenseType.TC_EHC) {
          sb.append("terracotta-ehcache-").append(version.getVersion(true)).append(".tar.gz");
          logger.debug("Kit name: {}", sb.toString());
          return sb.toString();
        }
        if (distribution.getLicenseType() == LicenseType.TC_DB) {
          sb.append("terracotta-db-").append(version.getVersion(true)).append(".tar.gz");
          logger.debug("Kit name: {}", sb.toString());
          return sb.toString();
        }
      }
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      if (version.getMajor() == 10) {
        if (version.getMinor() == 1) {
          if (distribution.getLicenseType() == LicenseType.TC_DB) {
            sb.append("SoftwareAGInstaller101_LATEST.jar");
            logger.debug("Kit name: {}", sb.toString());
            return sb.toString();
          }
        } else if (version.getMinor() == 2) {
          if (distribution.getLicenseType() == LicenseType.TC_DB) {
            sb.append("SoftwareAGInstaller102_LATEST.jar");
            logger.debug("Kit name: {}", sb.toString());
            return sb.toString();
          }
        } else if (version.getMinor() == 3) {
          if (distribution.getLicenseType() == LicenseType.TC_DB) {
            sb.append("SoftwareAGInstaller103_LATEST.jar");
            logger.debug("Kit name: {}", sb.toString());
            return sb.toString();
          }
        }
      }
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  public File getKitInstallationPath() {
    return kitInstallationPath;
  }
}
