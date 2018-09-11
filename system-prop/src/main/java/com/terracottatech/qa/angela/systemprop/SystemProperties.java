package com.terracottatech.qa.angela.systemprop;

import java.util.Arrays;
import java.util.List;


public class SystemProperties {

  public static final String SYSTEM_PROP_HOSTNAMES = "tc.qa.angela.hostnames";
  public static final String SYSTEM_PROP_ENABLE_SSH_REMOTE_AGENT_LAUNCHER = "tc.qa.angela.enableSshRemoteAgentLauncher";

  /**
   * Returns the hostnames provided as system property/
   * @return
   */
  public static List<String> hostnames() {
    String hostnames = System.getProperty(SYSTEM_PROP_HOSTNAMES);

    // returning null to identify if system property is not provided.
    if (hostnames == null) {
      return null;
    }

    // Otherwise spit the hostnames provided in the system property.
    return Arrays.asList(hostnames.split(","));
  }


  /**
   * Returns whether SSH remote agent launcher is enabled or not.
   * @return
   */
  public static boolean sshRemoteAgentLauncherEnabled() {
    return System.getProperty(SYSTEM_PROP_ENABLE_SSH_REMOTE_AGENT_LAUNCHER) != null;
  }
}
