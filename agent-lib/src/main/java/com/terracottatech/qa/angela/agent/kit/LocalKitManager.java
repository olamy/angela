package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.DirectoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
      logger.info("Using kitInstallationPath: {}", kitInstallationPath);
      Path path = Paths.get(kitInstallationPath);
      if (!Files.isDirectory(path)) {
        throw new IllegalArgumentException("kitInstallationPath: " + kitInstallationPath + " isn't a directory");
      }
      this.kitInstallationPath = path;
    } else if (rootInstallationPath != null) {
      Path localInstallerPath = resolveLocalInstallerPath();
      logger.info("Using local kit from: {}", localInstallerPath);
      if (!isValidLocalInstallerFilePath(offline, localInstallerPath)) {
        downloadLocalInstaller(localInstallerPath);
      }

      this.kitInstallationPath = rootInstallationPath.resolve(getDirFromArchive(localInstallerPath));

      if (isArchiveStale(offline, this.kitInstallationPath, localInstallerPath)) {
        throw new IllegalArgumentException("Local snapshot archive found to be older than " + STALE_SNAPSHOT_LIMIT + " milliseconds");
      }

      if (!Files.isDirectory(this.kitInstallationPath)) {
        logger.info("Local install not available at: {}", this.kitInstallationPath);
        if (offline) {
          throw new IllegalArgumentException("Can not install the kit version " + distribution + " in offline mode because" +
              " the kit compressed package is not available. Please run in online mode with an internet connection.");
        }
      }
      createLocalInstallFromInstaller(license, localInstallerPath);
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
      List<File> clientJars = Files.walk(kitInstallationPath.resolve(clientJarsRootFolderName))
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
    return kitInstallationPath.getFileName().toString();
  }

  private void downloadLocalInstaller(Path localInstallerFile) {
    URL[] urls = resolveKitUrls();
    URL kitUrl = urls[0];
    URL md5Url = urls[1];
    try {
      URLConnection urlConnection = kitUrl.openConnection();
      urlConnection.connect();

      int contentLength = urlConnection.getContentLength();
      logger.info("Downloading {} from {}", humanReadableByteCount(contentLength), kitUrl);

      createParentDirs(localInstallerFile);

      long lastProgress = -1;
      try (OutputStream fos = Files.newOutputStream(localInstallerFile);
           InputStream is = kitUrl.openStream()) {
        byte[] buffer = new byte[8192];
        long len = 0;
        int count;
        while ((count = is.read(buffer)) != -1) {
          len += count;

          long progress = 100 * len / contentLength;
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

      try (InputStream is = md5Url.openStream()) {
        Files.copy(is, Paths.get(localInstallerFile + ".md5"));
      }

      logger.debug("Success -> file downloaded successfully");
    } catch (IOException e) {
      // messed up download -> delete it
      logger.debug("Deleting: " + localInstallerFile.getParent() + " dir as it's messed up");
      DirectoryUtils.deleteQuietly(localInstallerFile.getParent());
      throw new UncheckedIOException(e);
    }
  }

  // Adapted from https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
  private static String humanReadableByteCount(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    String pre = "" + "KMGTPE".charAt(exp - 1);
    return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
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

  private static void createParentDirs(Path file) throws IOException {
    Objects.requireNonNull(file);
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  private String getDirFromArchive(Path localInstaller) {
    if (distribution.getPackageType() == KIT) {
      if (distribution.getVersion().getMajor() == 3) {
        return compressionUtils.getParentDirFromZip(localInstaller);
      }
      return compressionUtils.getParentDirFromTarGz(localInstaller);
    } else {
      return "TDB";
    }
  }

  private void createLocalInstallFromInstaller(License license, Path localInstallerFilename) {
    if (distribution.getPackageType() == KIT) {
      compressionUtils.extract(localInstallerFilename, rootInstallationPath);
    } else {
      compressionUtils.extractSag(getSandboxName(), license, localInstallerFilename, rootInstallationPath);
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

  private boolean isArchiveStale(boolean offline, Path installationPath, Path archiveFilePath) {
    // If we have a snapshot that is older than STALE_SNAPSHOT_LIMIT, we reload it
    // Use the archive because blah-blah to check the modified time, because the timestamp on the inflated directory
    // corresponds to the creation of the archive, and not to its download
    long timeSinceLastModified;
    try {
      timeSinceLastModified = System.currentTimeMillis() - Files.getLastModifiedTime(archiveFilePath).toMillis();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    if (!offline && distribution.getVersion().isSnapshot() && timeSinceLastModified > STALE_SNAPSHOT_LIMIT) {
      logger.info("Mode is online, and version is a snapshot older than: {} milliseconds. Reinstalling it.", STALE_SNAPSHOT_LIMIT);
      DirectoryUtils.deleteQuietly(installationPath);
      return true;
    }
    return false;
  }
}
