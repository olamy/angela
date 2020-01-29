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

import org.terracotta.angela.agent.Agent;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.tcconfig.License;
import org.terracotta.angela.common.topology.InstanceId;
import org.terracotta.angela.common.util.DirectoryUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Download the kit tarball from Kratos
 *
 * @author Aurelien Broszniowski
 */
public class RemoteKitManager extends KitManager {
  private static final Logger logger = LoggerFactory.getLogger(RemoteKitManager.class);

  private final Path workingKitInstallationPath; // the location where we will copy the install

  public RemoteKitManager(InstanceId instanceId, Distribution distribution, String kitInstallationName) {
    super(distribution);
    this.kitInstallationPath = rootInstallationPath.resolve(kitInstallationName);
    this.workingKitInstallationPath = Agent.WORK_DIR.resolve(instanceId.toString());
  }

  public File installKit(License license) {
    Path workingInstallPath = workingKitInstallationPath.resolve(distribution.getVersion().toString());
    Path workingCopyFromLocalInstall = createWorkingCopyFromLocalInstall(license, kitInstallationPath, workingInstallPath);
    logger.info("Working install is located in {}", workingCopyFromLocalInstall);
    return workingCopyFromLocalInstall.toFile();
  }

  private Path createWorkingCopyFromLocalInstall(License license, Path localInstall, Path workingInstallBasePath) {
    try {
      logger.info("Copying {} to {}", localInstall.toAbsolutePath(), workingInstallBasePath);
      Files.createDirectories(workingInstallBasePath);
      DirectoryUtils.copyDirectory(localInstall, workingInstallBasePath);
      if (license != null) {
        license.writeToFile(workingInstallBasePath.toFile());
      }

      cleanupPermissions(workingInstallBasePath);
      return workingInstallBasePath;
    } catch (IOException e) {
      throw new RuntimeException("Can not create working install", e);
    }
  }

  // TODO : duplicate
  private void cleanupPermissions(Path dest) {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return;
    }

    try (Stream<Path> walk = Files.walk(dest)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".sh") || name.endsWith("tms.jar");
          })
          .forEach(path -> {
            try {
              Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(path));
              perms.addAll(EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
              Files.setPosixFilePermissions(path, perms);
            } catch (IOException ioe) {
              throw new UncheckedIOException(ioe);
            }
          });
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public Path getWorkingKitInstallationPath() {
    return workingKitInstallationPath;
  }

  public boolean verifyKitAvailability(boolean offline) {
    logger.debug("verifying if the extracted kit is already available locally to setup an install");
    if (!Files.isDirectory(kitInstallationPath)) {
      logger.debug("Local kit installation is not available");
      return false;
    }
    return true;
  }

  public void deleteInstall(File installLocation) throws IOException {
    logger.info("deleting installation in {}", installLocation.getAbsolutePath());
    FileUtils.deleteDirectory(installLocation);
  }
}