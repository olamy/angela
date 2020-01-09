package com.terracottatech.qa.angela.common;

import com.terracottatech.qa.angela.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * Listing of all system properties supported in angela
 */
public enum AngelaProperties {
  ROOT_DIR("angela.rootDir", Paths.get("/data/angela").toAbsolutePath().toString()),
  KIT_INSTALLATION_DIR("angela.kitInstallationDir", null),
  DIRECT_JOIN("angela.directJoin", null),
  IGNITE_LOGGING("angela.igniteLogging", "false"),
  NODE_NAME("angela.nodeName", IpUtils.getHostName()),
  PORT_RANGE("angela.portRange", "1000"),
  SKIP_UNINSTALL("angela.skipUninstall", "false"),
  SSH_USERNAME("angela.ssh.userName", System.getProperty("user.name")),
  SSH_USERNAME_KEY_PATH("angela.ssh.userName.keyPath", null),
  SSH_STRICT_HOST_CHECKING("angela.ssh.strictHostKeyChecking", "true"),
  TMS_FULL_LOGGING("angela.tms.fullLogging", "false"),
  TSA_FULL_LOGGING("angela.tsa.fullLogging", "false"),

  // Deprecated properties
  KITS_DIR("kitsDir", Paths.get("/data/angela").toAbsolutePath().toString()),
  KIT_INSTALLATION_PATH("kitInstallationPath", null),
  ;

  private static final Logger logger = LoggerFactory.getLogger(AngelaProperties.class);

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

  /**
   * Returns the value of either the recommended or the deprecated property if the former wasn't specified.
   * Falls back to the default value of the new property if neither was specified.
   *
   * @param recommended the recommended property
   * @param deprecated  the deprecated property
   * @return the value of either the recommended, or the deprecated property. null if neither was specified and the default
   * value of the new property is null.
   */
  public static String getEitherOf(AngelaProperties recommended, AngelaProperties deprecated) {
    String value = recommended.getSpecifiedValue();
    if (value == null) {
      value = deprecated.getSpecifiedValue();
      if (value != null) {
        logger.warn("Deprecated property '{}' specified. Use '{}' instead to be future-ready", deprecated, recommended);
      }
    }

    if (value == null) {
      value = recommended.getDefaultValue();
    }
    return value;
  }
}
