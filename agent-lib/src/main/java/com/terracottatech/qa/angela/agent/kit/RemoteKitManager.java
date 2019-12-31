package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.DirectoryUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

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

  public File installKit(License license) {
    Path workingCopyFromLocalInstall = createWorkingCopyFromLocalInstall(license, kitInstallationPath);
    logger.info("Working install is located in {}", workingCopyFromLocalInstall);
    return workingCopyFromLocalInstall.toFile();
  }

  private Path createWorkingCopyFromLocalInstall(License license, Path localInstall) {
    try {
      Path workingInstallPath = workingKitInstallationPath.resolve(distribution.getVersion().toString());
      logger.debug("Copying {} to {}", localInstall.toAbsolutePath(), workingInstallPath);
      Files.createDirectories(workingInstallPath);
      DirectoryUtils.copyDirectory(localInstall, workingInstallPath);
      if (license != null) {
        license.writeToFile(workingInstallPath.toFile());
      }

      //install extra server jars
      if (System.getProperty("extraServerJars") != null && !System.getProperty("extraServerJars").contains("${")) {
        for (String path : System.getProperty("extraServerJars").split(File.pathSeparator)) {
          String serverLib = (distribution.getPackageType() == SAG_INSTALLER ? "TerracottaDB" + File.separator : "")
                             + "server" + File.separator + "plugins" + File.separator + "lib";
          Files.copy(Paths.get(path), workingInstallPath.resolve(localInstall.getFileName()).resolve(serverLib));
        }
      }
      compressionUtils.cleanupPermissions(workingInstallPath);
      return workingInstallPath;
    } catch (IOException e) {
      throw new RuntimeException("Can not create working install", e);
    }
  }

  public void deleteInstall(File installLocation) throws IOException {
    logger.info("deleting installation in {}", installLocation.getAbsolutePath());
    FileUtils.deleteDirectory(installLocation);
  }
}
