package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.DirectoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.agent.Agent.ROOT_DIR;
import static com.terracottatech.qa.angela.common.topology.LicenseType.GO;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * @author Aurelien Broszniowski
 */
public abstract class KitManager {
  private static final Logger logger = LoggerFactory.getLogger(KitManager.class);

  protected final Distribution distribution;
  protected final CompressionUtils compressionUtils = new CompressionUtils();
  protected final Path rootInstallationPath;  // the work directory where installs are stored for caching
  protected Path kitInstallationPath; // the extracted install to be used as a cached install

  public KitManager(Distribution distribution) {
    this.distribution = distribution;
    this.rootInstallationPath = distribution == null ? null : buildRootInstallationPath(distribution);
  }

  private Path buildRootInstallationPath(Distribution distribution) {
    PackageType packageType = distribution.getPackageType();
    if (packageType != KIT && packageType != SAG_INSTALLER) {
      // Can happen if someone adds a new packageType but doesn't provide a suitable handling here
      throw new RuntimeException("Can not resolve the local kit distribution package: " + packageType);
    }

    Path sagOrKitDir = ROOT_DIR.resolve(packageType == SAG_INSTALLER ? "sag" : "kits");
    DirectoryUtils.createAndValidateDir(sagOrKitDir);
    return sagOrKitDir.resolve(distribution.getVersion().getVersion(true));
  }

  /**
   * resolve installer file location
   *
   * @param offline
   * @return location of the installer archive file
   */
  protected boolean isValidLocalInstallerFilePath(boolean offline, Path localInstallerFile) {
    if (!Files.isRegularFile(localInstallerFile)) {
      logger.info("Kit {} is not an existing file", localInstallerFile.toAbsolutePath());
      return false;
    }

    // if we have a snapshot that is older than 24h, we reload it
    long timeSinceModified;
    try {
      timeSinceModified = System.currentTimeMillis() - Files.getLastModifiedTime(localInstallerFile).toMillis();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    if (!offline && distribution.getVersion().isSnapshot() && Math.abs(timeSinceModified) > TimeUnit.DAYS.toMillis(1)) {
      logger.debug("Our version is a snapshot, is older than 24h and we are not offline so we are deleting it to produce a reload.");
      DirectoryUtils.deleteQuietly(localInstallerFile.getParent());
      return false;
    }

    // snapshots and SAG installer have no MD5
    if (distribution.getVersion().isSnapshot() || distribution.getPackageType() != KIT) {
      return true;
    }

    String md5File = localInstallerFile.toAbsolutePath().toString() + ".md5";
    String md5FileHash;
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(md5File));
      md5FileHash = new String(bytes, StandardCharsets.US_ASCII).trim();
    } catch (NoSuchFileException nsfe) {
      // no MD5 file? let's consider the archive corrupt
      logger.warn("{} does not have corresponding {} secure hash file on disk, considering it corrupt", localInstallerFile, md5File);
      DirectoryUtils.deleteQuietly(localInstallerFile.getParent());
      return false;
    } catch (IOException ioe) {
      throw new RuntimeException("Error reading " + md5File, ioe);
    }

    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      try (InputStream fis = Files.newInputStream(localInstallerFile)) {
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
        DirectoryUtils.deleteQuietly(localInstallerFile.getParent());
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
  protected boolean isValidKitInstallationPath(Path installationPath) {
    if (!Files.isDirectory(installationPath)) {
      logger.debug("Install is not available.");
      return false;
    }
    return true;
  }

  Path resolveLocalInstallerPath() {
    logger.debug("Resolving the local installer name");
    Version version = distribution.getVersion();

    String fileName;
    if (distribution.getPackageType() == KIT) {
      if (version.getMajor() == 4) {
        fileName = "bigmemory-" + (distribution.getLicenseType() == GO ? "go-" : "max-") + version.getVersion(true) + ".tar.gz";
      } else if (version.getMajor() == 3) {
        fileName = "ehcache-clustered-" + version.getVersion(true) + "-kit.zip";
      } else {
        // 'db' was dropped from kits after 10.5.0.0.179, use 'terracotta' for such kits, 'terracotta-db' otherwise
        fileName = version.getMinor() > 5 || (version.getMinor() == 5 && version.getBuild_minor() >= 179) ? "terracotta-" : "terracotta-db-";
        fileName += version.getVersion(true) + ".tar.gz";
      }
    } else {
      // Corresponds to LicenseType TERRACOTTA and major version >=10
      fileName = getSAGInstallerName(version);
    }
    return rootInstallationPath.resolve(fileName);
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

  public Path getKitInstallationPath() {
    return kitInstallationPath;
  }
}
