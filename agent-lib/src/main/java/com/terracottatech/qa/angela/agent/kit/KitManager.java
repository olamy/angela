package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.DatatypeConverter;

import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * @author Aurelien Broszniowski
 */

public abstract class KitManager {

  private static final Logger logger = LoggerFactory.getLogger(KitManager.class);

  protected final Distribution distribution;
  protected final CompressionUtils compressionUtils = new CompressionUtils();
  protected final String rootInstallationPath;  // the work directory where installs are stored for caching
  protected File kitInstallationPath; // the extracted install to be used as a cached install

  public KitManager(Distribution distribution) {
    this.distribution = distribution;
    this.rootInstallationPath = distribution == null ? null : Agent.ROOT_DIR + File.separator + (distribution.getPackageType() == SAG_INSTALLER ? "sag" : "kits")
                                                              + File.separator + distribution.getVersion()
                                                                  .getVersion(false);
  }

  /**
   * resolve installer file location
   *
   * @param offline
   * @return location of the installer archive file
   */
  protected boolean isValidLocalInstallerFilePath(final boolean offline, File localInstallerFile) {
    if (!localInstallerFile.isFile()) {
      logger.debug("Kit {} is not an existing file", localInstallerFile.getAbsolutePath());
      return false;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstallerFile.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      logger.debug("Our version is a snapshot, is older than 24h and we are not offline so we are deleting it to produce a reload.");
      FileUtils.deleteQuietly(localInstallerFile.getParentFile());
      return false;
    }

    // snapshots have no MD5
    if (distribution.getVersion().isSnapshot()) {
      return true;
    }

    File md5File = new File(localInstallerFile.getAbsolutePath() + ".md5");
    StringBuilder sb = new StringBuilder();
    try (FileInputStream fis = new FileInputStream(md5File)) {
      byte[] buffer = new byte[64];
      while (true) {
        int read = fis.read(buffer);
        if (read == -1) {
          break;
        } else {
          sb.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
        }
      }
    } catch (FileNotFoundException fnfe) {
      // no MD5 file? let's consider the archive corrupt
      logger.warn("{} does not have corresponding {} secure hash file on disk, considering it corrupt", localInstallerFile, md5File);
      FileUtils.deleteQuietly(localInstallerFile.getParentFile());
      return false;
    } catch (IOException ioe) {
      throw new RuntimeException("Error reading " + md5File, ioe);
    }
    String md5FileHash = sb.toString().trim();

    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      try (FileInputStream fis = new FileInputStream(localInstallerFile)) {
        byte[] buffer = new byte[8192];
        while (true) {
          int read = fis.read(buffer);
          if (read == -1) {
            break;
          } else {
            md.update(buffer, 0, read);
          }
        }
      }
      String localInstallerFileHash = DatatypeConverter.printHexBinary(md.digest());

      if (!localInstallerFileHash.equalsIgnoreCase(md5FileHash)) {
        // MD5 does not match? let's consider the archive corrupt
        logger.warn("{} secure has does not match the contents of {} secure hash file on disk, considering it corrupt", localInstallerFile, md5File);
        FileUtils.deleteQuietly(localInstallerFile.getParentFile());
        return false;
      }
    } catch (NoSuchAlgorithmException nsae) {
      throw new RuntimeException("Missing MD5 secure hash implementation", nsae);
    } catch (IOException ioe) {
      throw new RuntimeException("Error reading " + localInstallerFile, ioe);
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
      if (version.getMajor() >= 10) {
        if (distribution.getLicenseType() == LicenseType.TC_DB) {
          sb.append(getSAGInstallerName(version));
          logger.debug("Kit name: {}", sb.toString());
          return sb.toString();
        }
      }
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  String getSAGInstallerName(Version version) {
    String versionValue = null;
    if (version.getMajor() >= 10) {
      if (version.getMinor() == 1) {
        versionValue = "101";
      } else if (version.getMinor() == 2) {
        versionValue = "102";
      } else if (version.getMinor() == 3) {
        if (version.getRevision() == 0) {
          versionValue = "103";
        } else if (version.getRevision() == 1) {
          versionValue = "104";
        } else if (version.getMinor() == 5) {
          versionValue = "105";
        }
      }
    } else if (version.getMajor() == 4) {
      if (version.getMinor() == 3) {
        if (version.getRevision() == 0) {
          versionValue = "98";
        } else if (version.getRevision() == 1) {
          versionValue = "99";
        } else if (version.getRevision() == 2) {
          versionValue = "910";
        } else if (version.getRevision() == 3) {
          versionValue = "912";
        } else if (version.getRevision() == 4) {
          versionValue = "101";
        } else if (version.getRevision() == 5) {
          versionValue = "102";
        } else if (version.getRevision() == 6) {
          versionValue = "103";
        } else if (version.getRevision() == 7) {
          versionValue = "104";
        }
      }
    }

    if (versionValue == null) {
      throw new IllegalArgumentException("getSAGInstallerName couldn't resolve the name for version " + version.toString());
    }

    return "SoftwareAGInstaller" + versionValue + "_LATEST.jar";
  }

  String getSandboxName(final Version version) {
    String sandbox = System.getProperty("sandbox");
    if (sandbox != null) {
      return sandbox;
    }
    throw new IllegalArgumentException("Missing Sandbox name : please pass -Dsandbox=");
  }

  public Distribution getDistribution() {
    return distribution;
  }

  public File getKitInstallationPath() {
    return kitInstallationPath;
  }
}
