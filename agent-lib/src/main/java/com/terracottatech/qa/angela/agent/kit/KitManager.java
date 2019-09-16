package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.DirectoryUtil;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

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
    this.rootInstallationPath = distribution == null ? null : buildRootInstallationPath(distribution);
  }

  private String buildRootInstallationPath(Distribution distribution) {
    StringBuilder sb = new StringBuilder();
    sb.append(Agent.ROOT_DIR).append(File.separator);

    PackageType packageType = distribution.getPackageType();
    if (packageType != KIT && packageType != SAG_INSTALLER) {
      // Can happen if someone adds a new packageType but doesn't provide a suitable handling here
      throw new RuntimeException("Can not resolve the local kit distribution package: " + packageType);
    }
    sb.append(packageType == SAG_INSTALLER ? "sag" : "kits").append(File.separator);

    DirectoryUtil.createAndAssertDir(new File(sb.toString()), packageType == SAG_INSTALLER ? "SAG installer" : "kits");

    sb.append(distribution.getVersion().getVersion(false));
    return sb.toString();
  }

  /**
   * resolve installer file location
   *
   * @param offline
   * @return location of the installer archive file
   */
  protected boolean isValidLocalInstallerFilePath(final boolean offline, File localInstallerFile) {
    if (!localInstallerFile.isFile()) {
      logger.info("Kit {} is not an existing file", localInstallerFile.getAbsolutePath());
      return false;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstallerFile.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      logger.debug("Our version is a snapshot, is older than 24h and we are not offline so we are deleting it to produce a reload.");
      FileUtils.deleteQuietly(localInstallerFile.getParentFile());
      return false;
    }

    // snapshots and SAG installer have no MD5
    if (distribution.getVersion().isSnapshot() || distribution.getPackageType() != KIT) {
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
      try {
        Files.walk(localInstallerFile.getParentFile().toPath())
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(File::delete);
      } catch (IOException e) {
        throw new RuntimeException("Cannot recursively delete local installer location (" + localInstallerFile.getParentFile().toPath() + ")");
      }
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
        logger.warn("{} secure hash does not match the contents of {} secure hash file on disk, considering it corrupt", localInstallerFile, md5File);
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
   * e.g. : /data/tsamanager/kits/10.5.0/terracotta-10.5.0-SNAPSHOT
   *
   * @param installationPath
   * @return location of the install to be used to create the working install
   */
  protected boolean isValidKitInstallationPath(File installationPath) {
    if (!installationPath.isDirectory()) {
      logger.debug("Install is not available.");
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
        return sb.append(version.getVersion(true)).append(".tar.gz").toString();
      } else if (version.getMajor() == 3) {
        return sb.append("ehcache-clustered-").append(version.getVersion(true)).append("-kit.zip").toString();
      } else {
        if (version.getMinor() > 5 || (version.getMinor() == 5 && version.getBuild_minor() >= 179)) {
          // 'db' was dropped from kits after 10.5.0.0.179
          sb.append("terracotta-");
        } else {
          // Continue to old the old name to support older kits
          sb.append("terracotta-db-");
        }
        return sb.append(version.getVersion(true)).append(".tar.gz").toString();
      }
    } else {
      // Corresponds to LicenseType TERRACOTTA and major version >=10
      return sb.append(getSAGInstallerName(version)).toString();
    }
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
        }
      } else if (version.getMinor() == 5) {
        versionValue = "105";
      } else if (version.getMinor() == 7) {
        versionValue = "107";
      }
    } else {
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

  String getSandboxName() {
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
