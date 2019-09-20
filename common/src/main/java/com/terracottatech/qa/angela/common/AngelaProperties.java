package com.terracottatech.qa.angela.common;

import com.terracottatech.qa.angela.common.util.IpUtils;

import java.nio.file.Paths;

/**
 * Listing of all system properties supported in angela
 */
public enum AngelaProperties {
  DIRECT_JOIN("tc.qa.directjoin", null),
  KITS_DIR("kitsDir", Paths.get("data" ,"angela").toAbsolutePath().toString()),
  KIT_INSTALLATION_PATH("kitInstallationPath", null),
  IGNITE_LOGGING("tc.qa.ignite.logging", "false"),
  NODE_NAME("tc.qa.nodeName", IpUtils.getHostName()),
  PORT_RANGE("tc.qa.portrange", "1000"),
  SKIP_UNINSTALL("tc.qa.angela.skipUninstall", "false"),
  SSH_USERNAME("tc.qa.angela.ssh.user.name", System.getProperty("user.name")),
  SSH_USERNAME_KEY_PATH("tc.qa.angela.ssh.user.name.key.path", null),
  SSH_STRICT_HOST_CHECKING("tc.qa.angela.ssh.strictHostKeyChecking", "true"),
  TMS_FULL_LOGGING("angela.tms.log.full", "false"),
  TSA_FULL_LOGGING("angela.tsa.log.full", "false"),
  ;

  AngelaProperties(String propertyName, String defaultValue) {
    this.propertyName = propertyName;
    this.defaultValue = defaultValue;
  }

  private final String propertyName;
  private final String defaultValue;

  public String getDefaultValue() {
    return defaultValue;
  }

  public String getSpecifiedValue() {
    return System.getProperty(propertyName);
  }

  public String getValue() {
    String specifiedValue = getSpecifiedValue();
    return specifiedValue == null || specifiedValue.isEmpty() ? getDefaultValue() : specifiedValue;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public void setProperty(String value) {
    System.setProperty(propertyName, value);
  }

  public void clearProperty() {
    System.clearProperty(propertyName);
  }
}
