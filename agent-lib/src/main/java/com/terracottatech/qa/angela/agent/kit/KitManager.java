package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class KitManager extends ParentKitManager {

  private static final Logger logger = LoggerFactory.getLogger(KitManager.class);

  private final String workingKitInstallationPath; // the location where we will copy the install

  public KitManager(InstanceId instanceId, Distribution distribution, final String kitInstallationName) {
    super(distribution);

    this.kitInstallationPath = new File(this.rootInstallationPath, kitInstallationName);
    this.workingKitInstallationPath = rootInstallationPath + File.separator + "work" + File.separator + instanceId;
  }

  public boolean verifyKitAvailability(final boolean offline) {
    logger.info("verifying if the extracted kit is already available locally to setup an install");

    if (!isValidKitInstallationPath(offline, kitInstallationPath)) {
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

  private File createWorkingCopyFromLocalInstall(final License license, final File localInstall) {
    try {
      logger.info("Copying {} to {}", localInstall.getAbsolutePath(), workingKitInstallationPath);
      File workingInstallPath = new File(workingKitInstallationPath);
      boolean res = workingInstallPath.mkdirs();
      logger.info("Directories created? {}", res);
      FileUtils.copyDirectory(localInstall, workingInstallPath);
      license.writeToFile(workingInstallPath);

      //install extra server jars
      if (System.getProperty("extraServerJars") != null && !System.getProperty("extraServerJars").contains("${")) {
        for (String path : System.getProperty("extraServerJars").split(File.pathSeparator)) {
          String serverlib = (distribution.getPackageType() == SAG_INSTALLER ? "TerracottaDB" + File.separator : "")
                             + "server" + File.separator + "plugins" + File.separator + "lib";
          FileUtils.copyFileToDirectory(new File(path),
              new File(workingKitInstallationPath + File.separator + localInstall.getName(), serverlib));
        }
      }
      compressionUtils.cleanupPermissions(workingInstallPath);
      return workingInstallPath;
    } catch (IOException e) {
      throw new RuntimeException("Can not create working install", e);
    }
  }

  public void deleteInstall(final File installLocation) throws IOException {
    logger.info("deleting installation in {}", installLocation.getAbsolutePath());
    FileUtils.deleteDirectory(installLocation);
  }
}
