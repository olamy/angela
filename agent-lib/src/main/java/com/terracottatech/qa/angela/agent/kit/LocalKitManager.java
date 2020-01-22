package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.KitResolver;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.util.DirectoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */
public class LocalKitManager extends KitManager {
  private static final Logger logger = LoggerFactory.getLogger(LocalKitManager.class);
  private static final long STALE_SNAPSHOT_LIMIT = TimeUnit.DAYS.toMillis(1);

  private final Map<String, File> clientJars = new HashMap<>();
  private final KitResolver kitResolver;

  public LocalKitManager(Distribution distribution) {
    super(distribution);

    if (distribution != null) {
      final ServiceLoader<KitResolver> kitResolvers = ServiceLoader.load(KitResolver.class);
      KitResolver currentKitResolver = null;
      int kitResolverCount = 0;
      for (KitResolver kitResolver : kitResolvers) {
        kitResolverCount++;
        if (kitResolver.supports(distribution.getLicenseType())) {
          if (currentKitResolver != null) {
            throw new IllegalStateException("Found several service implementation for KitResolver for LicenseType " + distribution
                .getLicenseType());
          }
          currentKitResolver = kitResolver;
        }
      }
      if (currentKitResolver == null) {
        throw new IllegalArgumentException("Current LicenceType " + distribution.getLicenseType() + " can't find a corresponding KitResolver service (" + kitResolverCount + " services available)");
      } else {
        this.kitResolver = currentKitResolver;
      }
    } else {
      this.kitResolver = null;
    }
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
      Path localInstallerPath = rootInstallationPath.resolve(
          kitResolver.resolveLocalInstallerPath(distribution.getVersion(), distribution.getLicenseType(), distribution.getPackageType()));
      logger.info("Using local kit from: {}", localInstallerPath);
      if (!isValidLocalInstallerFilePath(offline, localInstallerPath)) {
        kitResolver.downloadLocalInstaller(distribution.getVersion(), distribution.getLicenseType(), distribution.getPackageType(), localInstallerPath);
      }

      this.kitInstallationPath = kitResolver.resolveKitInstallationPath(distribution.getVersion(), distribution.getPackageType(), localInstallerPath, rootInstallationPath);

      if (isArchiveStale(offline, this.kitInstallationPath, localInstallerPath)) {
        throw new IllegalArgumentException("Local snapshot archive found to be older than " + STALE_SNAPSHOT_LIMIT + " milliseconds");
      }

      if (!Files.isDirectory(this.kitInstallationPath)) {
        logger.info("Local install not available at: {}", this.kitInstallationPath);
        if (offline) {
          throw new IllegalArgumentException("Can not install the kit version " + distribution + " in offline mode because" +
                                             " the kit compressed package is not available. Please run in online mode with an internet connection.");
        }
        kitResolver.createLocalInstallFromInstaller(distribution.getVersion(), distribution.getPackageType(), license, localInstallerPath, rootInstallationPath);
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
