package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


/**
 * Download the kit tarball from Kratos
 *
 * @author Aurelien Broszniowski
 */
public class KitManager implements Serializable {

  private static final Logger logger = LoggerFactory.getLogger(KitManager.class);

  private CompressionUtils compressionUtils = new CompressionUtils();
  private final InstanceId instanceId;
  private Distribution distribution;
  private final String kitInstallationPath;
  private final String localInstallationPath;

  public KitManager(InstanceId instanceId, Topology topology) {
    this.instanceId = instanceId;
    this.distribution = topology.getDistribution();
    this.kitInstallationPath = topology.getKitInstallationPath();
    this.localInstallationPath = Agent.WORK_DIR + File.separator + (distribution.getPackageType() == SAG_INSTALLER ? "sag" : "kits") + File.separator
                                 + distribution.getVersion().getVersion(false);
  }


  /**
   * define kratos url for version
   *
   * @return url of package
   */
  protected URL resolveUrl() {
    try {
      Version version = distribution.getVersion();
      PackageType packageType = distribution.getPackageType();
      LicenseType licenseType = distribution.getLicenseType();

      if (packageType == KIT) {
        if (version.getMajor() == 4) {
          StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
          if (version.getMinor() <= 2) {
            if (version.isSnapshot()) {
              sb.append(version.getMajor()).append(".").append(version.getMinor()).append("/");
            } else {
              sb.append(version.getMajor())
                  .append(".")
                  .append(version.getMinor())
                  .append(".")
                  .append(version.getRevision())
                  .append("/");
            }
          } else {
            if (version.isSnapshot()) {
              sb.append("trunk/");
            } else {
              sb.append(version.getMajor())
                  .append(".")
                  .append(version.getMinor())
                  .append(".")
                  .append(version.getRevision())
                  .append(".")
                  .append(version.getBuild_major())
                  .append(".")
                  .append(version.getBuild_minor())
                  .append("/");
            }
          }
          sb.append("bigmemory-");
          if (licenseType == LicenseType.GO) {
            sb.append("go-");
          } else if (licenseType == LicenseType.MAX) {
            sb.append("max-");
          }
          sb.append(version.getVersion(true)).append(".tar.gz");
          return new URL(sb.toString());
        } else if (version.getMajor() == 5) {
          if (licenseType == LicenseType.TC_EHC) {
            StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
            sb.append(version.getVersion(false)).append("/");
            sb.append("uberkit-").append(version.getVersion(true)).append("-kit.zip");
            return new URL(sb.toString());
          } else if (licenseType == LicenseType.OS) {
            String realVersion = version.getRealVersion(true, false);
            StringBuilder sb = new StringBuilder(" https://oss.sonatype.org/service/local/artifact/maven/redirect?")
                .append("g=org.ehcache&")
                .append("a=ehcache-clustered&")
                .append("c=kit&")
                .append("e=zip&")
                .append(realVersion.contains("SNAPSHOT") ? "r=snapshots&" : "r=releases&")
                .append("v=" + realVersion);
            return new URL(sb.toString());
          }
        } else if (version.getMajor() == 10) {
          if (licenseType == LicenseType.TC_EHC) {
            StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
            sb.append(version.getVersion(false)).append("/");
            sb.append("terracotta-ehcache-").append(version.getVersion(true)).append(".tar.gz");
            return new URL(sb.toString());
          }
          if (licenseType == LicenseType.TC_DB) {
            StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
            sb.append(version.getVersion(false)).append("/");
            sb.append("terracotta-db-").append(version.getVersion(true)).append(".tar.gz");
            return new URL(sb.toString());
          }
        }
      } else if (packageType == SAG_INSTALLER) {
        if (version.getMajor() == 10) {
          if (version.getMinor() == 1) {
            if (licenseType == LicenseType.TC_DB) {
              return new URL("http://aquarius_va.ame.ad.sag/PDShare/SoftwareAGInstaller101_LATEST.jar");
            }
          } else if (version.getMinor() == 2) {
            if (licenseType == LicenseType.TC_DB) {
              return new URL("http://aquarius_va.ame.ad.sag/PDShare/SoftwareAGInstaller102_LATEST.jar");
            }

          }
        }
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Can not resolve the kratos url for the distribution package", e);
    }
    throw new RuntimeException("Can not resolve the kratos url for the distribution package");
  }

  private void downloadLocalInstaller(final File localInstallerFilename) {
    URL kitUrl = resolveUrl();
    try {
      URLConnection urlConnection = kitUrl.openConnection();
      urlConnection.connect();

      int contentlength = urlConnection.getContentLength();
      logger.info("Downloading {} - {} bytes", kitUrl, contentlength);

      createParentDirs(localInstallerFilename);

      long lastProgress = -1;
      try (OutputStream fos = new FileOutputStream(localInstallerFilename);
           InputStream is = kitUrl.openStream()) {
        byte[] buffer = new byte[8192];
        long len = 0;
        int count;
        while ((count = is.read(buffer)) != -1) {
          len += count;

          long progress = 100 * len / contentlength;
          if (progress % 10 == 0 && progress > lastProgress) {
            logger.info("Download progress = {}%", progress);
            lastProgress = progress;
          }
          fos.write(buffer, 0, count);
        }
      }

      logger.debug("Success -> file downloaded succesfully");
    } catch (IOException e) {
      throw new RuntimeException("Can not download kit located at " + kitUrl, e);
    }
  }

  /**
   */
  public File installKit(License license, final boolean offline) {
    if (kitInstallationPath != null) {
      logger.info("Using kitInstallationPath: \"{}\"", kitInstallationPath);
      if (!new File(kitInstallationPath).isDirectory()) {
        throw new IllegalArgumentException("You set the kitIntallationPath property to ["
                                           + kitInstallationPath + "] but the location ins't a directory");
      }
      return new File(kitInstallationPath);
    } else {
      logger.info("getting install from the kit/installer");
      File localInstallerFilename = new File(resolveLocalInstallerFilename());
      File localInstallPath = new File(this.localInstallationPath
                                       + File.separatorChar + getDirFromArchive(localInstallerFilename));
      if (!isValidLocalInstallPath(offline, localInstallPath)) {
        logger.debug("Local install not available");

        if (!isValidLocalInstallerFilePath(offline, localInstallerFilename)) {
          logger.debug("Local installer not available");
          if (offline) {
            throw new IllegalArgumentException("Can not install the kit version " + distribution + " in offline mode because the kit compressed package is not available. Please run in online mode with an internet connection.");
          }
          downloadLocalInstaller(localInstallerFilename);
        }
        createLocalInstallFromInstaller(license, localInstallerFilename);
      }
      File workingCopyFromLocalInstall = createWorkingCopyFromLocalInstall(localInstallPath);
      logger.info("Working install is located in {}", workingCopyFromLocalInstall);
      return workingCopyFromLocalInstall;
    }
  }

  /**
   * Resolve local install path that will be used to create a working copy
   * <p>
   * This is the directory containing the exploded terracotta install, that will be copied to give a
   * single instance working installation
   * <p>
   * e.g. : /data/tsamanager/kits/10.1.0/terracotta-db-10.1.0-SNAPSHOT
   *
   * @param offline
   * @param localInstallPath
   * @return location of the install to be used to create the working install
   */
  private boolean isValidLocalInstallPath(final boolean offline, final File localInstallPath) {
    if (!localInstallPath.isDirectory()) {
      logger.debug("Install is not available.");
      return false;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstallPath.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      try {
        logger.debug("Version is snapshot and older than 24h and mode is online, so we reinstall it.");
        FileUtils.deleteDirectory(localInstallPath);
      } catch (IOException e) {
        return false;
      }
      return false;
    }
    return true;
  }

  private String getDirFromArchive(final File localInstaller) {
    try {
      if (distribution.getPackageType() == KIT) {
        if (distribution.getVersion().getMajor() == 4) {
          return compressionUtils.getParentDirFromTarGz(localInstaller);
        } else if (distribution.getVersion().getMajor() == 5) {
          return compressionUtils.getParentDirFromZip(localInstaller);
        } else if (distribution.getVersion().getMajor() == 10) {
          return compressionUtils.getParentDirFromTarGz(localInstaller);
        }
      } else if (distribution.getPackageType() == SAG_INSTALLER) {
        if (distribution.getVersion().getMajor() == 10) {
          return "TDB";
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Can not resolve the local kit distribution package", e);
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  /**
   * resolve installer file location
   *
   * @param offline
   * @return location of the installer archive file
   */
  private boolean isValidLocalInstallerFilePath(final boolean offline, File localInstallerFilename) {
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

  private String resolveLocalInstallerFilename() {
    logger.debug("Resolving the local installer name");
    StringBuilder sb = new StringBuilder(this.localInstallationPath);
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
        }
      }
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  private void createLocalInstallFromInstaller(License license, final File localInstallerFilename) {
    File dest = new File(this.localInstallationPath);
    if (distribution.getPackageType() == KIT) {
      compressionUtils.extract(localInstallerFilename, dest);
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      compressionUtils.extractSag(distribution.getVersion(), license, localInstallerFilename, dest);
    } else {
      throw new RuntimeException("Can not resolve the local kit distribution package");
    }
  }

  private File createWorkingCopyFromLocalInstall(final File localInstall) {
    File workingInstall = new File(Agent.WORK_DIR + File.separator + instanceId);
    try {
      logger.info("Copying {} to {}", localInstall.getAbsolutePath(), workingInstall.getAbsolutePath());
      workingInstall.mkdirs();
      Files.copy(localInstall.toPath(), workingInstall.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
      //install extra server jars
      if (System.getProperty("extraServerJars") != null && !System.getProperty("extraServerJars").contains("${")) {
        for (String path : System.getProperty("extraServerJars").split(File.pathSeparator)) {
          FileUtils.copyFileToDirectory(new File(path),
              new File(workingInstall, "server" + File.separator + "plugins" + File.separator + "lib"));

        }
      }
      compressionUtils.cleanupPermissions(workingInstall);
    } catch (IOException e) {
      throw new RuntimeException("Can not create working install", e);
    }
    return workingInstall;
  }

  private static void createParentDirs(File file) throws IOException {
    Objects.requireNonNull(file);
    File parent = file.getCanonicalFile().getParentFile();
    if (parent != null) {
      parent.mkdirs();
      if (!parent.isDirectory()) {
        throw new IOException("Unable to create parent directories of " + file);
      }
    }
  }

  public void deleteInstall(final File installLocation) throws IOException {
    if (kitInstallationPath == null) {
      // only delete when not in galvan mode
      logger.info("deleting installation in {}", installLocation.getAbsolutePath());
      FileUtils.deleteDirectory(installLocation);
    }
  }
}
