package com.terracottatech.qa.angela.agent.client;

import com.terracottatech.qa.angela.common.topology.InstanceId;

import java.io.File;

import static com.terracottatech.qa.angela.agent.Agent.ROOT_DIR;
import static com.terracottatech.qa.angela.agent.Agent.ROOT_DIR_SYSPROP_NAME;

/**
 * @author Aurelien Broszniowski
 */

public class RemoteClientManager {

  private final String rootInstallationPath;  // the work directory where installs are stored for caching
  private final File kitInstallationPath;

  public RemoteClientManager(final InstanceId instanceId) {
    String localWorkRootDir;
    final String dir = System.getProperty(ROOT_DIR_SYSPROP_NAME);
    if (dir == null || dir.isEmpty()) {
      localWorkRootDir = new File(ROOT_DIR).getAbsolutePath();
    } else if (dir.startsWith(".")) {
      throw new IllegalArgumentException("Can not use relative path for the ROOT_DIR. Please use a fixed one.");
    } else {
      localWorkRootDir = dir;
    }

    this.rootInstallationPath = localWorkRootDir;
    this.kitInstallationPath = new File(this.rootInstallationPath, instanceId.toString());
  }

  public File getClientInstallationPath() {
    return kitInstallationPath;
  }
}
