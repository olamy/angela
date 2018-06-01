package com.terracottatech.qa.angela.common.tcconfig;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vmad
 */
public class SecurityRootDirectory implements Serializable {

  static final String TRUSTED_AUTHORITY_DIR_NAME = "trusted-authority";
  static final String IDENTITY_DIR_NAME = "identity";
  static final String ACCESS_CONTROL_DIR_NAME = "access-control";


  private final Map<String, byte[]> identityMap;
  private final Map<String, byte[]> trustedAuthorityMap;
  private final Map<String, byte[]> accessControlMap;

  private SecurityRootDirectory(Path securityRootDirectory) {
    Path identityDir = securityRootDirectory.resolve(IDENTITY_DIR_NAME);
    if (Files.exists(identityDir)) {
      identityMap = new HashMap<>();
      storeContentsToMap(identityDir, identityMap);
    } else {
      identityMap = null;
    }

    Path trustedAuthorityDir = securityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    if (Files.exists(trustedAuthorityDir)) {
      trustedAuthorityMap = new HashMap<>();
      storeContentsToMap(trustedAuthorityDir, trustedAuthorityMap);
    } else {
      trustedAuthorityMap = null;
    }

    Path accessControlDir = securityRootDirectory.resolve(ACCESS_CONTROL_DIR_NAME);
    if (Files.exists(accessControlDir)) {
      accessControlMap = new HashMap<>();
      storeContentsToMap(accessControlDir, accessControlMap);
    } else {
      accessControlMap = null;
    }

  }

  public static SecurityRootDirectory securityRootDirectory(URL securityRootDirectoryUrl) {
    try {
      return new SecurityRootDirectory(Paths.get(securityRootDirectoryUrl.toURI()));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static SecurityRootDirectory securityRootDirectory(Path securityRootDirectoryPath) {
    return new SecurityRootDirectory(securityRootDirectoryPath);
  }

  private static void storeContentsToDir(Map<String, byte[]> map, Path directory) {

    for (Map.Entry<String, byte[]> entry : map.entrySet()) {
      Path filePath = directory.resolve(entry.getKey());

      byte[] fileContents = entry.getValue();

      try {
        Files.write(filePath, fileContents);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create file " + filePath, e);
      }
    }
  }

  private static void storeContentsToMap(Path directory, Map<String, byte[]> map) {

    try {
      Files.list(directory).forEach((path) -> {
        try {
          map.put(path.getFileName().toString(), IOUtils.toByteArray(Files.newInputStream(path)));
        } catch (IOException e) {
          throw new RuntimeException("Unable to read file " + path, e);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Unable to read directory " + directory, e);
    }
  }

  public void createSecurityRootDirectory(Path newSecurityRootDirectory) {
    Path identityDir = newSecurityRootDirectory.resolve(IDENTITY_DIR_NAME);
    if (identityMap != null) {
      try {
        Files.createDirectories(identityDir);
        storeContentsToDir(identityMap, identityDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + identityDir, e);
      }
    }

    Path trustedAuthorityDir = newSecurityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    if (trustedAuthorityMap != null) {
      try {
        Files.createDirectories(trustedAuthorityDir);
        storeContentsToDir(trustedAuthorityMap, trustedAuthorityDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + trustedAuthorityDir, e);
      }
    }

    Path accessControlDir = newSecurityRootDirectory.resolve(ACCESS_CONTROL_DIR_NAME);
    if (accessControlMap != null) {
      try {
        Files.createDirectories(accessControlDir);
        storeContentsToDir(accessControlMap, accessControlDir);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory " + accessControlDir, e);
      }
    }

  }
}
