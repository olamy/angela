/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.agent.kit;

import org.terracotta.angela.KitResolver;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.tcconfig.License;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static java.util.stream.Collectors.toList;

/**
 * @author Aurelien Broszniowski
 */
public class LocalKitManager extends KitManager {
  private static final Logger logger = LoggerFactory.getLogger(LocalKitManager.class);
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
      logger.info("Local kit found at: {}", localInstallerPath);
      if (!isValidLocalInstallerFilePath(offline, localInstallerPath)) {
        logger.info("Local kit at: {} found to be invalid. Downloading a fresh installer", localInstallerPath);
        kitResolver.downloadLocalInstaller(distribution.getVersion(), distribution.getLicenseType(), distribution.getPackageType(), localInstallerPath);
      }

      this.kitInstallationPath = kitResolver.resolveKitInstallationPath(distribution.getVersion(), distribution.getPackageType(), localInstallerPath, rootInstallationPath);

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
}
