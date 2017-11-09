package com.terracottatech.qa.angela.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.terracottatech.qa.angela.kit.distribution.Distribution;
import com.terracottatech.qa.angela.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.tcconfig.LicenseConfig;
import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.Version;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.topology.PackageType.SAG_INSTALLER;


/**
 * Download the kit tarball from Kratos
 *
 * @author Aurelien Broszniowski
 */
public class KitManager implements Serializable {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public static String defaultDir = "/data/tsamanager";
  private CompressionUtils compressionUtils = new CompressionUtils();

  public KitManager() {
  }
/*

  public File downloadKit(final Version version) throws TsaManagerException {
    verifyLocalInstallation(version);
    File kitLocation = verifyLocalInstaller(version);
    if (!kitLocation.isFile()) {
      URL kitUrl = resolveUrl(version);
      download(kitUrl, kitLocation);
    }
    return kitLocation;
  }
*/

  /**
   * Verify that we have the up-to-date installer archive
   *
   * @param distributionController Terracotta controller
   * @return location of the installer archive file
   */
  private String resolveLocalDir(final DistributionController distributionController) {
    String kitsDir = System.getProperty("kitsDir");
    if (kitsDir == null) {
      kitsDir = defaultDir;
    } else if (kitsDir.isEmpty()) {
      kitsDir = defaultDir;
    } else if (kitsDir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the kitsDir. Please use a fixed one.");
    }
    return kitsDir + File.separator + (distributionController.getPackageType() == SAG_INSTALLER ? "sag" : "kits") + File.separator
           + distributionController.getVersion(false);
  }

  /**
   * define kratos url for version
   *
   * @param distributionController
   * @return url of package
   */
  protected URL resolveUrl(final DistributionController distributionController) {
    try {
      Version version  = distributionController.getVersion();
      if (distributionController.getPackageType() == KIT) {
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
          if (distributionController.getLicenseType() == LicenseType.GO) {
            sb.append("go-");
          } else if (distributionController.getLicenseType() == LicenseType.MAX) {
            sb.append("max-");
          }
          sb.append(version.getVersion(true)).append(".tar.gz");
          return new URL(sb.toString());
        } else if (version.getMajor() == 5) {
          if (distributionController.getLicenseType() == LicenseType.TC_EHC) {
            StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
            sb.append(version.getVersion(false)).append("/");
            sb.append("uberkit-").append(version.getVersion(true)).append("-kit.zip");
            return new URL(sb.toString());
          } else if (distributionController.getLicenseType() == LicenseType.OS) {
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
          if (distributionController.getLicenseType() == LicenseType.TC_EHC) {
            StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
            sb.append(version.getVersion(false)).append("/");
            sb.append("terracotta-ehcache-").append(version.getVersion(true)).append(".tar.gz");
            return new URL(sb.toString());
          }
          if (distributionController.getLicenseType() == LicenseType.TC_DB) {
            StringBuilder sb = new StringBuilder("http://kits.terracotta.eur.ad.sag/releases/");
            sb.append(version.getVersion(false)).append("/");
            sb.append("terracotta-db-").append(version.getVersion(true)).append(".tar.gz");
            return new URL(sb.toString());
          }
        }
      } else if (distributionController.getPackageType() == SAG_INSTALLER) {
        if (version.getMajor() == 10) {
          if (version.getMinor() == 1) {
            if (distributionController.getLicenseType() == LicenseType.TC_DB) {
              return new URL("http://aquarius_va.ame.ad.sag/PDShare/SoftwareAGInstaller101_LATEST.jar");
            }
          } else if (version.getMinor() == 2) {
            if (distributionController.getLicenseType() == LicenseType.TC_DB) {
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

  private void download(final URL kitUrl, final File kitLocation) {
    int count = 0;

    try {
      URLConnection urlConnection = kitUrl.openConnection();
      urlConnection.connect();

      int contentlength = urlConnection.getContentLength();
      System.out.println("Length to download = " + contentlength);

      Files.createParentDirs(kitLocation);

      OutputStream fos = new FileOutputStream(kitLocation);

      InputStream is = new BufferedInputStream(kitUrl.openStream());

      byte[] buffer = new byte[8192];
      long len1 = 0;
      while ((count = is.read(buffer)) != -1) {
        len1 += count;

        System.out.print(" progress = " + (100 * len1 / contentlength));
        fos.write(buffer, 0, count);
      }
      fos.flush();
      fos.close();
      is.close();

      logger.info("Success -> file downloaded succesfully. returning 'success' code");
    } catch (IOException e) {
      logger.error("Exception in update process : ", e);
    }
    logger.info("Failed -> file download failed. returning 'error' code");
  }

  private void download2(final URL kitUrl, final File kitLocation) {
    // TODO : add a file lock machanism to be sure that two processes don't download the same file
    InputStream inputStream = null;
    FileOutputStream outputStream = null;
    try {
      Files.createParentDirs(kitLocation);

      URLConnection connection = kitUrl.openConnection();
      inputStream = connection.getInputStream();
      outputStream = new FileOutputStream(kitLocation);
      int contentLength = connection.getContentLength();
      if (contentLength != -1) {

      } else {

      }


      ByteStreams.copy(inputStream, outputStream);
    } catch (IOException e) {
      throw new RuntimeException("Can not download the kit from kratos", e);
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) { }
      }

      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (IOException e) { }
      }
    }
  }

  /**
   * -> tcConfigId is Tc config id for the current install, kitLocation is tarball dir, version is version for specificities
   * /work/kitinstall/156523236586232/hostname:111/
   * \--------------/ \-------------/ \----------/
   * base install     tcconfig id     tc instance
   * <p/>
   * TODO  Change the method args
   */
  public File installKit(final DistributionController distributionController, LicenseConfig licenseConfig, final boolean offline) {
    File localInstall = resolveLocalInstall(distributionController, offline);
    if (localInstall == null) {
      logger.debug("Local install not available");
      File localInstaller = resolveLocalInstaller(distributionController, offline);
      if (localInstaller == null) {
        logger.debug("Local installer not available");
        if (offline) {
          throw new IllegalArgumentException("Can not install the kit version " + distributionController.getVersion().toString() + " in offline mode because the kit compressed package is not available. Please run in online mode with an internet connection.");
        }
        localInstaller = downloadLocalInstaller(distributionController);
      }
      localInstall = createLocalInstallFromInstaller(distributionController, licenseConfig, localInstaller);
    }
    return createWorkingCopyFromLocalInstall(localInstall);
  }

  /**
   * Resolve local install location
   * This is the directory containing the exploded terracotta install, that will be copied to give a
   * single instance working installation
   * <p>
   * e.g. : /data/tsamanager/kits/10.1.0/terracotta-db-10.1.0-SNAPSHOT
   *
   * @param distributionController Terracotta controller
   * @param offline
   * @return location of the install to be used to create the working install
   */
  protected File resolveLocalInstall(final DistributionController distributionController, final boolean offline) {
    logger.debug("Resolving local install location");
    File localInstaller = resolveLocalInstaller(distributionController, offline);
    if (localInstaller == null) {
      return null;
    }
    logger.debug("Installer is available. Checking install.");
    File localInstall = new File(resolveLocalInstallName(distributionController)
                                 + File.separatorChar + getDirFromArchive(distributionController, localInstaller));

    if (!localInstall.isDirectory()) {
      logger.debug("Install is not available.");
      return null;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distributionController.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstall.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      try {
        logger.debug("Version is snapshot and older than 24h and mode is online, so we reinstall it.");
        FileUtils.deleteDirectory(localInstall);
      } catch (IOException e) {
        return null;
      }
      return null;
    }
    return localInstall;
  }

  private String getDirFromArchive(final DistributionController distributionController, final File localInstaller) {
    try {
      if (distributionController.getPackageType() == KIT) {
        if (distributionController.getVersion().getMajor() == 4) {
          return compressionUtils.getParentDirFromTarGz(localInstaller);
        } else if (distributionController.getVersion().getMajor() == 5) {
          return compressionUtils.getParentDirFromZip(localInstaller);
        } else if (distributionController.getVersion().getMajor() == 10) {
          return compressionUtils.getParentDirFromTarGz(localInstaller);
        }
      } else if (distributionController.getPackageType() == SAG_INSTALLER) {
        if (distributionController.getVersion().getMajor() == 10) {
          return "Terracotta-" + distributionController.getVersion().toString();
        }

      }
    } catch (Exception e) {
      throw new RuntimeException("Can not resolve the local kit distribution package", e);
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  private String resolveLocalInstallName(final DistributionController distributionController) {
    return resolveLocalDir(distributionController);
  }

  /**
   * resolve installer file location
   *
   * @param distributionController Terracotta controller
   * @param offline
   * @return location of the installer archive file
   */
  private File resolveLocalInstaller(final DistributionController distributionController, final boolean offline) {
    File localInstaller = new File(resolveLocalInstallerName(distributionController));
    if (!localInstaller.isFile()) {
      logger.debug("Kit {} is not an existing file", localInstaller.getAbsolutePath());
      return null;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distributionController.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstaller.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      logger.debug("Our version is a snapshot, is older than 24h and we are not offline so we are deleting it to produce a reload.");
      FileUtils.deleteQuietly(localInstaller);
      return null;
    }
    return localInstaller;
  }

  String resolveLocalInstallerName(final DistributionController distributionController) {
    logger.debug("Resolving the local installer name");
    StringBuilder sb = new StringBuilder(resolveLocalDir(distributionController));
    sb.append(File.separator);
    Version version = distributionController.getVersion();
    if (distributionController.getPackageType() == KIT) {

      if (version.getMajor() == 4) {
        sb.append("bigmemory-");
        if (distributionController.getLicenseType() == LicenseType.GO) {
          sb.append("go-");
        } else if (distributionController.getLicenseType() == LicenseType.MAX) {
          sb.append("max-");
        }
        sb.append(version.getVersion(true)).append(".tar.gz");
        logger.debug("Kit name: {}", sb.toString());
        return sb.toString();
      } else if (version.getMajor() == 5) {
        if (distributionController.getLicenseType() == LicenseType.TC_EHC) {
          sb.append("uberkit-").append(version.getVersion(true)).append("-kit.zip");
        } else if (distributionController.getLicenseType() == LicenseType.OS) {
          sb.append("ehcache-clustered-").append(version.getRealVersion(true, false)).append("-kit.zip");
        }
        logger.debug("Kit name: {}", sb.toString());
        return sb.toString();
      } else if (version.getMajor() == 10) {
        if (distributionController.getLicenseType() == LicenseType.TC_EHC) {
          sb.append("terracotta-ehcache-").append(version.getVersion(true)).append(".tar.gz");
          logger.debug("Kit name: {}", sb.toString());
          return sb.toString();
        }
        if (distributionController.getLicenseType() == LicenseType.TC_DB) {
          sb.append("terracotta-db-").append(version.getVersion(true)).append(".tar.gz");
          logger.debug("Kit name: {}", sb.toString());
          return sb.toString();
        }
      }
    } else if (distributionController.getPackageType() == SAG_INSTALLER) {
      if (version.getMajor() == 10) {
        if (version.getMinor() == 1) {
          if (distributionController.getLicenseType() == LicenseType.TC_DB) {
            sb.append("SoftwareAGInstaller101_LATEST.jar");
            logger.debug("Kit name: {}", sb.toString());
            return sb.toString();
          }
        } else if (version.getMinor() == 2) {
          if (distributionController.getLicenseType() == LicenseType.TC_DB) {
            sb.append("SoftwareAGInstaller102_LATEST.jar");
            logger.debug("Kit name: {}", sb.toString());
            return sb.toString();
          }
        }
      }
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  private File downloadLocalInstaller(final DistributionController distributionController) {
    URL installerUrl = resolveUrl(distributionController);
    File installerLocation = new File(resolveLocalInstallerName(distributionController));
    download(installerUrl, installerLocation);
    return installerLocation;
  }

  private File createLocalInstallFromInstaller(final DistributionController distributionController, LicenseConfig licenseConfig, final File localInstaller) {
    File dest = new File(resolveLocalInstallName(distributionController));
    if (distributionController.getPackageType() == KIT) {
      File localInstall = new File(resolveLocalInstallName(distributionController) + File.separatorChar + getDirFromArchive(distributionController, localInstaller));
      compressionUtils.extract(localInstaller, dest);
      return localInstall;
    } else if (distributionController.getPackageType() == SAG_INSTALLER) {
      File localInstallDir = new File(resolveLocalInstallName(distributionController) + File.separatorChar + "TDB");
      compressionUtils.extractSag(distributionController.getVersion(), licenseConfig, localInstaller, dest, localInstallDir);
      return dest;
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  private File createWorkingCopyFromLocalInstall(final File localInstall) {
    File workingInstall = new File(localInstall + "_" + new SimpleDateFormat("MMddHHmmssSS").format(new Date()));
    try {
      FileUtils.copyDirectory(localInstall, workingInstall);
      //install extra server jars
      if (System.getProperty("extraServerJars") != null && !System.getProperty("extraServerJars").contains("${")) {
        for (String path : System.getProperty("extraServerJars").split(File.pathSeparator)) {
          FileUtils.copyFileToDirectory(new File(path),
              new File(workingInstall, "server" + File.separator + "plugins" + File.separator + "lib"));

        }
      }
      compressionUtils.cleanup(workingInstall);
    } catch (IOException e) {
      throw new RuntimeException("Can not create working install", e);
    }
    return workingInstall;
  }

}
