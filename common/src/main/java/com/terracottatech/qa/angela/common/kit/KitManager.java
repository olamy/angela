package com.terracottatech.qa.angela.common.kit;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;


/**
 * Download the kit tarball from Kratos
 *
 * @author Aurelien Broszniowski
 */
public class KitManager implements Serializable {

  public static String DEFAULT_KIT_DIR = "/data/tsamanager";
  public static final String KITS_DIR;
  static {
    String dir = System.getProperty("kitsDir");
    if (dir == null) {
      KITS_DIR = DEFAULT_KIT_DIR;
    } else if (dir.isEmpty()) {
      KITS_DIR = DEFAULT_KIT_DIR;
    } else if (dir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the KITS_DIR. Please use a fixed one.");
    } else {
      KITS_DIR = dir;
    }
  }


  private final Logger logger = LoggerFactory.getLogger(getClass());

  private CompressionUtils compressionUtils = new CompressionUtils();
  private final String topologyId;
  private Distribution distribution;

  public KitManager(String topologyId, final Distribution distribution) {
    this.topologyId = topologyId;
    this.distribution = distribution;
  }

  /**
   * Verify that we have the up-to-date installer archive
   *
   * @return location of the installer archive file
   */
  private String resolveLocalDir() {
    return KITS_DIR + File.separator + (distribution.getPackageType() == SAG_INSTALLER ? "sag" : "kits") + File.separator
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

  private void download(final URL kitUrl, final File kitLocation) {
    try {
      URLConnection urlConnection = kitUrl.openConnection();
      urlConnection.connect();

      int contentlength = urlConnection.getContentLength();
      System.out.println("Length to download = " + contentlength);

      createParentDirs(kitLocation);

      OutputStream fos = new FileOutputStream(kitLocation);

      InputStream is = new BufferedInputStream(kitUrl.openStream());

      byte[] buffer = new byte[8192];
      long len1 = 0;
      int count;
      while ((count = is.read(buffer)) != -1) {
        len1 += count;

        System.out.print("\r progress = " + (100 * len1 / contentlength) + "%");
        fos.write(buffer, 0, count);
      }
      System.out.println("");
      fos.flush();
      fos.close();
      is.close();

      logger.info("Success -> file downloaded succesfully. returning 'success' code");
    } catch (IOException e) {
      throw new RuntimeException("Can not download kit located at " + kitUrl, e);
    }
    logger.info("Failed -> file download failed. returning 'error' code");
  }

  private void download2(final URL kitUrl, final File kitLocation) {
    // TODO : add a file lock machanism to be sure that two processes don't download the same file
    InputStream inputStream = null;
    FileOutputStream outputStream = null;
    try {
      createParentDirs(kitLocation);

      URLConnection connection = kitUrl.openConnection();
      inputStream = connection.getInputStream();
      outputStream = new FileOutputStream(kitLocation);
      int contentLength = connection.getContentLength();
      if (contentLength != -1) {

      } else {

      }


      copy(inputStream, outputStream);
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
  public File installKit(License license, final boolean offline) {
    File localInstall = resolveLocalInstall(offline);
    if (localInstall == null) {
      logger.debug("Local install not available");
      File localInstaller = resolveLocalInstaller(offline);
      if (localInstaller == null) {
        logger.debug("Local installer not available");
        if (offline) {
          throw new IllegalArgumentException("Can not install the kit version " + distribution + " in offline mode because the kit compressed package is not available. Please run in online mode with an internet connection.");
        }
        localInstaller = downloadLocalInstaller();
      }
      localInstall = createLocalInstallFromInstaller(license, localInstaller);
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
   * @param offline
   * @return location of the install to be used to create the working install
   */
  protected File resolveLocalInstall(final boolean offline) {
    logger.debug("Resolving local install location");
    File localInstaller = resolveLocalInstaller(offline);
    if (localInstaller == null) {
      return null;
    }
    logger.debug("Installer is available. Checking install.");
    File localInstall = new File(resolveLocalInstallName()
                                 + File.separatorChar + getDirFromArchive(localInstaller));

    if (!localInstall.isDirectory()) {
      logger.debug("Install is not available.");
      return null;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
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
          return "Terracotta-" + distribution.getVersion().toString();
        }

      }
    } catch (Exception e) {
      throw new RuntimeException("Can not resolve the local kit distribution package", e);
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  private String resolveLocalInstallName() {
    return resolveLocalDir();
  }

  /**
   * resolve installer file location
   *
   * @param offline
   * @return location of the installer archive file
   */
  private File resolveLocalInstaller(final boolean offline) {
    File localInstaller = new File(resolveLocalInstallerName());
    if (!localInstaller.isFile()) {
      logger.debug("Kit {} is not an existing file", localInstaller.getAbsolutePath());
      return null;
    }

    // if we have a snapshot that is older than 24h, we reload it
    if (!offline && distribution.getVersion().isSnapshot()
        && Math.abs(System.currentTimeMillis() - localInstaller.lastModified()) > TimeUnit.DAYS.toMillis(1)) {
      logger.debug("Our version is a snapshot, is older than 24h and we are not offline so we are deleting it to produce a reload.");
      FileUtils.deleteQuietly(localInstaller);
      return null;
    }
    return localInstaller;
  }

  String resolveLocalInstallerName() {
    logger.debug("Resolving the local installer name");
    StringBuilder sb = new StringBuilder(resolveLocalDir());
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

  private File downloadLocalInstaller() {
    URL installerUrl = resolveUrl();
    File installerLocation = new File(resolveLocalInstallerName());
    download(installerUrl, installerLocation);
    return installerLocation;
  }

  private File createLocalInstallFromInstaller(License license, final File localInstaller) {
    File dest = new File(resolveLocalInstallName());
    if (distribution.getPackageType() == KIT) {
      File localInstall = new File(resolveLocalInstallName() + File.separatorChar + getDirFromArchive(localInstaller));
      compressionUtils.extract(localInstaller, dest);
      return localInstall;
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      File localInstallDir = new File(resolveLocalInstallName() + File.separatorChar + "TDB");
      compressionUtils.extractSag(distribution.getVersion(), license, localInstaller, dest, localInstallDir);
      return dest;
    }
    throw new RuntimeException("Can not resolve the local kit distribution package");
  }

  private File createWorkingCopyFromLocalInstall(final File localInstall) {
    File workingInstall = new File(KITS_DIR + "/" + topologyId.replace(':', '_'), localInstall.getName());
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

  public File getInstallLocation() {
      return null;
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

  private static long copy(InputStream from, OutputStream to) throws IOException {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    byte[] buf = new byte[8192];
    long total = 0L;

    while(true) {
      int r = from.read(buf);
      if (r == -1) {
        return total;
      }

      to.write(buf, 0, r);
      total += (long)r;
    }
  }

}
