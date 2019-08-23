package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.Version;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.TimeUnit;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static com.terracottatech.qa.angela.common.topology.LicenseType.EHCACHE_OS;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */
public class LocalKitManager extends KitManager {
  private static final Logger logger = LoggerFactory.getLogger(LocalKitManager.class);
  private static final long STALE_SNAPSHOT_LIMIT = TimeUnit.DAYS.toMillis(1);

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
      String file = resolveLocalInstallerFilename();
      logger.info("Using local kit from: {}", file);
      File localInstallerFilename = new File(file);
      if (!isValidLocalInstallerFilePath(offline, localInstallerFilename)) {
        downloadLocalInstaller(localInstallerFilename);
      }

      this.kitInstallationPath = new File(rootInstallationPath, getDirFromArchive(localInstallerFilename));

      if (isArchiveStale(offline, this.kitInstallationPath, localInstallerFilename)) {
        throw new IllegalArgumentException("Local snapshot archive found to be older than " + STALE_SNAPSHOT_LIMIT + " milliseconds");
      }

      if (!isValidKitInstallationPath(this.kitInstallationPath)) {
        logger.debug("Local install not available");
        if (offline) {
          throw new IllegalArgumentException("Can not install the kit version " + distribution + " in offline mode because" +
              " the kit compressed package is not available. Please run in online mode with an internet connection.");
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
    URL[] urls = resolveKitUrls();
    URL kitUrl = urls[0];
    URL md5Url = urls[1];
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

      try (OutputStream fos = new FileOutputStream(localInstallerFile + ".md5");
           InputStream is = md5Url.openStream()) {
        byte[] buffer = new byte[64];
        int count;
        while ((count = is.read(buffer)) != -1) {
          fos.write(buffer, 0, count);
        }
      }

      logger.debug("Success -> file downloaded successfully");
    } catch (IOException e) {
      // messed up download -> delete it
      FileUtils.deleteQuietly(localInstallerFile.getParentFile());
      throw new RuntimeException("Can not download kit located at " + kitUrl, e);
    }
  }


  /**
   * define Kratos/Sonatype URL for version
   *
   * @return Array, [url of package, url of md5]
   */
  private URL[] resolveKitUrls() {
    URL kitUrl = null;
    URL md5Url = null;
    try {
      Version version = distribution.getVersion();
      LicenseType licenseType = distribution.getLicenseType();

      if (distribution.getPackageType() == KIT) {
        String fullVersionString = version.getVersion(false);
        String pathMatch = "";
        if (version.getMajor() == 4 && version.isSnapshot()) {
          // at least in 4.x we upload snapshot kits under "trunk" version (in 10.x there are no snapshots yet)
          fullVersionString = "trunk";
          pathMatch = version.getVersion(false); //we'll have to restrict by filename
        }

        if (licenseType == EHCACHE_OS) {
          String realVersion = version.getVersion(true);
          StringBuilder sb = new StringBuilder("https://oss.sonatype.org/service/local/artifact/maven/redirect?")
              .append("g=org.ehcache&")
              .append("a=ehcache-clustered&")
              .append("c=kit&")
              .append(realVersion.contains("SNAPSHOT") ? "r=snapshots&" : "r=releases&")
              .append("v=").append(realVersion).append("&")
              .append("e=zip");
          kitUrl = new URL(sb.toString());
          md5Url = new URL(sb.toString() + ".md5");
        } else {
          String format = "http://kits.terracotta.eur.ad.sag:3000/release/download_latest?branch=%s&showrc=1&tag=%s&md5=%s&filename=%s";
          kitUrl = new URL(String.format(format, fullVersionString, licenseType.getKratosTag(), "false", pathMatch));
          md5Url = new URL(String.format(format, fullVersionString, licenseType.getKratosTag(), "true", pathMatch));
        }
      } else {
        if (version.getMajor() >= 10) {
          kitUrl = new URL("http://aquarius_va.ame.ad.sag/PDShare/" + getSAGInstallerName(version));
        }
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Can not resolve the url for the distribution package: " + distribution, e);
    }
    return new URL[]{kitUrl, md5Url};
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
    if (distribution.getPackageType() == KIT) {
      if (distribution.getVersion().getMajor() == 3) {
        return compressionUtils.getParentDirFromZip(localInstaller);
      }
      return compressionUtils.getParentDirFromTarGz(localInstaller);
    } else {
      return "TDB";
    }
  }

  private void createLocalInstallFromInstaller(License license, File localInstallerFilename) {
    File dest = new File(this.rootInstallationPath);
    if (distribution.getPackageType() == KIT) {
      compressionUtils.extract(localInstallerFilename, dest);
    } else {
      compressionUtils.extractSag(getSandboxName(), distribution.getVersion(), license, localInstallerFilename, dest);
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

  private boolean isArchiveStale(boolean offline, File installationPath, File archiveFilePath) {
    // If we have a snapshot that is older than STALE_SNAPSHOT_LIMIT, we reload it
    // Use the archive because blah-blah to check the modified time, because the timestamp on the inflated directory
    // corresponds to the creation of the archive, and not to its download
    long lastModified = archiveFilePath.lastModified();
    long timeSinceLastModified = System.currentTimeMillis() - lastModified;
    if (!offline && distribution.getVersion().isSnapshot() && timeSinceLastModified > STALE_SNAPSHOT_LIMIT) {
      try {
        logger.info("Mode is online, and version is a snapshot older than: {} milliseconds. Reinstalling it.", STALE_SNAPSHOT_LIMIT);
        FileUtils.deleteDirectory(installationPath);
      } catch (IOException e) {
        return true;
      }
      return true;
    }
    return false;
  }
}
