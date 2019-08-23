package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.io.File;
import java.io.IOException;

import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * Download the kit tarball from Kratos
 *
 * @author Aurelien Broszniowski
 */
public class RemoteKitManager extends KitManager {
  private static final Logger logger = LoggerFactory.getLogger(RemoteKitManager.class);

  private final File workingKitInstallationPath; // the location where we will copy the install

  public RemoteKitManager(InstanceId instanceId, Distribution distribution, String kitInstallationName) {
    super(distribution);
    this.kitInstallationPath = new File(this.rootInstallationPath, kitInstallationName);
    this.workingKitInstallationPath = new File(Agent.WORK_DIR, instanceId.toString());
  }

  public File getWorkingKitInstallationPath() {
    return workingKitInstallationPath;
  }

  public boolean verifyKitAvailability(boolean offline) {
    logger.debug("verifying if the extracted kit is already available locally to setup an install");
    if (!isValidKitInstallationPath(kitInstallationPath)) {
      logger.debug("Local kit installation is not available");
      return false;
    }
    return true;
  }

  public File installKit(License license) {
    File workingCopyFromLocalInstall = createWorkingCopyFromLocalInstall(license, kitInstallationPath);
    logger.info("Working install is located in {}", workingCopyFromLocalInstall);
    return workingCopyFromLocalInstall;
  }

  private File createWorkingCopyFromLocalInstall(License license, File localInstall) {
    try {
      File workingInstallPath = new File(workingKitInstallationPath, distribution.getVersion().toString());
      logger.debug("Copying {} to {}", localInstall.getAbsolutePath(), workingInstallPath);
      boolean res = workingInstallPath.mkdirs();
      logger.debug("Directories created? {}", res);
      FileUtils.copyDirectory(localInstall, workingInstallPath);
      if (license != null) {
        license.writeToFile(workingInstallPath);
      }

      //install extra server jars
      if (System.getProperty("extraServerJars") != null && !System.getProperty("extraServerJars").contains("${")) {
        for (String path : System.getProperty("extraServerJars").split(File.pathSeparator)) {
          String serverlib = (distribution.getPackageType() == SAG_INSTALLER ? "TerracottaDB" + File.separator : "")
                             + "server" + File.separator + "plugins" + File.separator + "lib";
          FileUtils.copyFileToDirectory(new File(path),
              new File(workingInstallPath + File.separator + localInstall.getName(), serverlib));
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
