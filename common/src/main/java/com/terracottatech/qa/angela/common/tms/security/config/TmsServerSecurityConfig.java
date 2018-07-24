package com.terracottatech.qa.angela.common.tms.security.config;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Yakov Feldman
 *
 * This class is used to overwrite security properties inside TMS configuration file tmc.properties
 *
 * Use example:
 *     TmsServerSecurityConfig tmsServerSecurityConfig =
 *     new TmsServerSecurityConfig.Builder().with($ -> {
 *               $.tmsSecurityRootDirectory = "/config/security-root-dir";
 *               $.tmsSecurityHttpsEnabled = "true";
 *               $.tmsSecurityAuthenticationScheme = null;
 *               $.tmsSecurityAuthorizationScheme = null;
 *               $.tmsSecurityRootDirectoryConnectionDefault = "/config/server/security-root-dir";
 *               $.tmsSecurityAuditDirectory = "";
 *               $.deprecatedSecurityRootDirectory = null;
 *               $.deprecatedSecurityLevel = null;
 *     }).build();
 *
 * Descritpion:
 *  if( filed=null ) then
 *        {this property will be removed from original TMS configuration file tmc.properties }
 *  if( filed is not assigned ) then
 *        {this property will be not updated in original configuration file tmc.properties }
 *  else
 *      { property will be overwritten with assigned value }
 *
 *
 */

public class TmsServerSecurityConfig {

  public static final String NOT_SET_VALUE = "was not set";

  public static final String SECURITY_ROOT_DIRECTORY = "tms.security.root.directory";
  public static final String AUDIT_DIRECTORY = "tms.security.audit.directory";
  public static final String HTTPS_ENABLED = "tms.security.https.enabled";
  public static final String AUTHENTICATION_SCHEME = "tms.security.authentication.scheme";
  public static final String AUTHORIZATION_SCHEME = "tms.security.authorization.scheme";
  public static final String SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT = "tms.security.root.directory.connection.default";

  public static final String DEPRECATED_SECURITY_ROOT_DIRECTORY = "security.root.directory";
  public static final String DEPRECATED_SECURITY_LEVEL = "security.level";

  private String tmsSecurityRootDirectory;
  private String tmsSecurityHttpsEnabled;
  private String tmsSecurityAuthenticationScheme;
  private String tmsSecurityAuthorizationScheme;
  private String tmsSecurityRootDirectoryConnectionDefault;
  private String tmsSecurityAuditDirectory;
  @Deprecated
  private String deprecatedSecurityRootDirectory;
  @Deprecated
  private String deprecatedSecurityLevel;

  private TmsServerSecurityConfig() {
  }

  public String getTmsSecurityRootDirectory() {
    return tmsSecurityRootDirectory;
  }

  public String getTmsSecurityHttpsEnabled() {
    return tmsSecurityHttpsEnabled;
  }

  public String getTmsSecurityAuthenticationScheme() {
    return tmsSecurityAuthenticationScheme;
  }

  public String getTmsSecurityAuthorizationScheme() {
    return tmsSecurityAuthorizationScheme;
  }

  public String getTmsSecurityRootDirectoryConnectionDefault() {
    return tmsSecurityRootDirectoryConnectionDefault;
  }

  public String getTmsSecurityAuditDirectory() {
    return tmsSecurityAuditDirectory;
  }

  public String getDeprecatedSecurityRootDirectory() {
    return deprecatedSecurityRootDirectory;
  }

  public String getDeprecatedSecurityLevel() {
    return deprecatedSecurityLevel;
  }

  @Override
  public String toString() {
    return String.format("tmsSecurityRootDirectory:%s, tmsSecurityHttpsEnabled:%s, tmsSecurityAuthenticationScheme:%s," +
            " tmsSecurityAuthorizationScheme:%s, tmsSecurityRootDirectoryConnectionDefault:%s," +
            " tmsSecurityAuditDirectory:%s, deprecatedSecurityRootDirectory:%s, deprecatedSecurityLevel:%s",
        getTmsSecurityRootDirectory(),
        getTmsSecurityHttpsEnabled(),
        getTmsSecurityAuthenticationScheme(),
        getTmsSecurityAuthorizationScheme(),
        getTmsSecurityRootDirectoryConnectionDefault(),
        getTmsSecurityAuditDirectory(),
        getDeprecatedSecurityRootDirectory(),
        getDeprecatedSecurityLevel());
  }

  public Map<String, String> toMap() {

    Map<String, String> map = new HashMap<>();
    String property;

    if (!((property = getTmsSecurityRootDirectory()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(SECURITY_ROOT_DIRECTORY, property);
    }

    if (!((property = getTmsSecurityHttpsEnabled()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(HTTPS_ENABLED, property);
    }

    if (!((property = getTmsSecurityAuthenticationScheme()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(AUTHENTICATION_SCHEME, property);
    }

    if (!((property = getTmsSecurityAuthorizationScheme()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(AUTHORIZATION_SCHEME, property);
    }

    if (!((property = getTmsSecurityRootDirectoryConnectionDefault()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT, property);
    }

    if (!((property = getTmsSecurityAuditDirectory()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(AUDIT_DIRECTORY, property);
    }

    if (!((property = getDeprecatedSecurityRootDirectory()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(DEPRECATED_SECURITY_ROOT_DIRECTORY, property);
    }

    if (!((property = getDeprecatedSecurityLevel()) != null && property.equals(NOT_SET_VALUE))) {
      map.put(DEPRECATED_SECURITY_LEVEL, property);
    }

    return map;
  }

  public static class Builder {
    public String tmsSecurityRootDirectory = NOT_SET_VALUE;
    public String tmsSecurityHttpsEnabled = NOT_SET_VALUE;
    public String tmsSecurityAuthenticationScheme = NOT_SET_VALUE;
    public String tmsSecurityAuthorizationScheme = NOT_SET_VALUE;
    public String tmsSecurityRootDirectoryConnectionDefault = NOT_SET_VALUE;
    public String tmsSecurityAuditDirectory = NOT_SET_VALUE;
    @Deprecated
    public String deprecatedSecurityRootDirectory = NOT_SET_VALUE;
    @Deprecated
    public String deprecatedSecurityLevel = NOT_SET_VALUE;


    public Builder with(
        Consumer<Builder> builderFunction) {
      builderFunction.accept(this);
      return this;
    }

    public TmsServerSecurityConfig build() {
      TmsServerSecurityConfig config = new TmsServerSecurityConfig();
      config.tmsSecurityRootDirectory = tmsSecurityRootDirectory;
      config.tmsSecurityHttpsEnabled = tmsSecurityHttpsEnabled;
      config.tmsSecurityAuthenticationScheme = tmsSecurityAuthenticationScheme;
      config.tmsSecurityAuthorizationScheme = tmsSecurityAuthorizationScheme;
      config.tmsSecurityRootDirectoryConnectionDefault = tmsSecurityRootDirectoryConnectionDefault;
      config.tmsSecurityAuditDirectory = tmsSecurityAuditDirectory;
      config.deprecatedSecurityRootDirectory = deprecatedSecurityRootDirectory;
      config.deprecatedSecurityLevel = deprecatedSecurityLevel;
      return config;
    }
  }

}
