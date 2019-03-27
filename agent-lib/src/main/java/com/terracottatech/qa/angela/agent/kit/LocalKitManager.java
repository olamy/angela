package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;
import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */

public class LocalKitManager extends KitManager {

  private static final Logger logger = LoggerFactory.getLogger(LocalKitManager.class);
  private final Map<String, File> clientJars = new HashMap<>();

  public LocalKitManager(Distribution distribution) {
    super(distribution);
  }

  public void setupLocalInstall(License license, String kitInstallationPath, boolean offline) {
    if (kitInstallationPath != null) {
      logger.info("Using kitInstallationPath: \"{}\"", kitInstallationPath);
      if (!new File(kitInstallationPath).isDirectory()) {
        throw new IllegalArgumentException("You set the kitIntallationPath property to ["
                                           + kitInstallationPath + "] but the location ins't a directory");
      }
      this.kitInstallationPath = new File(kitInstallationPath);
    } else if (rootInstallationPath != null) {
      logger.debug("getting install from the kit/installer");
      File localInstallerFilename = new File(resolveLocalInstallerFilename());
      if (!isValidLocalInstallerFilePath(offline, localInstallerFilename)) {
        downloadLocalInstaller(localInstallerFilename);
      }

      this.kitInstallationPath = new File(this.rootInstallationPath
                                          + File.separatorChar + getDirFromArchive(localInstallerFilename));

      if (!isValidKitInstallationPath(offline, this.kitInstallationPath)) {
        logger.debug("Local install not available");

        logger.debug("Local installer not available");
        if (offline) {
          throw new IllegalArgumentException("Can not install the kit version " + distribution + " in offline mode because the kit compressed package is not available. Please run in online mode with an internet connection.");
        }
        createLocalInstallFromInstaller(license, localInstallerFilename);
      }
    }
    if (this.kitInstallationPath != null) {
      initClientJarsMap();
      logger.info("Local distribution is located in {}", this.kitInstallationPath);
    }
  }

  private void initClientJarsMap() {
    if (kitInstallationPath == null) {
      // no configured kit -> no client jars
      return;
    }

    try {
      String clientJarsRootFolderName = distribution.createDistributionController()
          .clientJarsRootFolderName(distribution);
      List<File> clientJars = Files.walk(new File(kitInstallationPath, clientJarsRootFolderName).toPath())
          .filter(Files::isRegularFile)
          .map(Path::toFile)
          .collect(toList());

      for (File clientJar : clientJars) {
        /*
         * Identify files by reading the JAR's MANIFEST.MF file and reading the "Bundle-SymbolicName" attribute.
         * This is provided by all OSGi-enabled JARs (all TC jars do) and is meant to figure out if two JAR files
         * are providing the same thing, barring any version differences.
         * Only include jars that have their Bundle-SymbolicName start with "com.terracotta".
         */
        String bundleSymbolicName = loadManifestBundleSymbolicName(clientJar);
        if (bundleSymbolicName != null && bundleSymbolicName.startsWith("com.terracotta")) {
          this.clientJars.put(bundleSymbolicName, clientJar);
        }
      }
      logger.debug("Kit client jars : {}", this.clientJars);
    } catch (IOException ioe) {
      throw new RuntimeException("Error listing client jars in " + kitInstallationPath, ioe);
    }
  }

  public String getKitInstallationName() {
    return kitInstallationPath.getName();
  }

  private void downloadLocalInstaller(File localInstallerFile) {
    URL kitUrl = resolveUrl();
    try {
      URLConnection urlConnection = kitUrl.openConnection();
      urlConnection.connect();

      int contentlength = urlConnection.getContentLength();
      logger.info("Downloading {} - {} bytes", kitUrl, contentlength);

      createParentDirs(localInstallerFile);

      long lastProgress = -1;
      try (OutputStream fos = new FileOutputStream(localInstallerFile);
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

      // snapshots and SAG installer have no MD5
      if (distribution.getVersion().isSnapshot() || distribution.getPackageType() != KIT) {
        return;
      }

      URL md5Url = new URL(kitUrl.toString() + ".md5");
      try (OutputStream fos = new FileOutputStream(localInstallerFile + ".md5");
           InputStream is = md5Url.openStream()) {
        byte[] buffer = new byte[64];
        int count;
        while ((count = is.read(buffer)) != -1) {
          fos.write(buffer, 0, count);
        }
      }

      logger.debug("Success -> file downloaded succesfully");
    } catch (IOException e) {
      // messed up download -> delete it
      FileUtils.deleteQuietly(localInstallerFile.getParentFile());
      throw new RuntimeException("Can not download kit located at " + kitUrl, e);
    }
  }


  /**
   * define kratos url for version
   *
   * @return url of package
   */
  private URL resolveUrl() {
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
        if (version.getMajor() >= 10) {
          if (licenseType == LicenseType.TC_DB) {
            StringBuilder sb = new StringBuilder("http://aquarius_va.ame.ad.sag/PDShare/");
            sb.append(getSAGInstallerName(version));
            return new URL(sb.toString());
          }
        }
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Can not resolve the kratos url for the distribution package", e);
    }
    throw new RuntimeException("Can not resolve the kratos url for the distribution package");
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

  private String getDirFromArchive(File localInstaller) {
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
        if (distribution.getVersion().getMajor() >= 10) {
          return "TDB";
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Can not resolve the local kit distribution package: " + distribution + " from: " + localInstaller, e);
    }
    throw new RuntimeException("Can not resolve the local kit distribution package: " + distribution + " from: " + localInstaller);
  }

  private void createLocalInstallFromInstaller(License license, File localInstallerFilename) {
    File dest = new File(this.rootInstallationPath);
    if (distribution.getPackageType() == KIT) {
      compressionUtils.extract(localInstallerFilename, dest);
    } else if (distribution.getPackageType() == SAG_INSTALLER) {
      compressionUtils.extractSag(getSandboxName(distribution.getVersion()), distribution.getVersion(), license, localInstallerFilename, dest);
    } else {
      throw new RuntimeException("Can not resolve the local kit distribution package");
    }
  }

  public File equivalentClientJar(File file) {
    String sourceBundleSymbolicName = loadManifestBundleSymbolicName(file);
    return clientJars.get(sourceBundleSymbolicName);
  }

  private String loadManifestBundleSymbolicName(File file) {
    try {
      if (file.getName().endsWith(".jar")) {
        try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(file))) {
          Manifest manifest = jarInputStream.getManifest();
          return manifest == null ? null : manifest.getMainAttributes().getValue("Bundle-SymbolicName");
        }
      } else {
        return null;
      }
    } catch (IOException ioe) {
      logger.error("Error loading the JAR manifest of " + file, ioe);
      return null;
    }
  }
}
