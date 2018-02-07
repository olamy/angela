package com.terracottatech.qa.angela.common.tcconfig;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.IDENTITY_DIR_NAME;
import static com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory.TRUSTED_AUTHORITY_DIR_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author vmad
 */
public class SecurityRootDirectoryTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testSerialization() throws Exception {
    Path securityRootDirectory = temporaryFolder.newFolder().toPath();

    Path identityDir = securityRootDirectory.resolve(IDENTITY_DIR_NAME);
    Files.createDirectory(identityDir);
    Files.write(identityDir.resolve("file1"), "identity-file1 contents".getBytes());
    Files.write(identityDir.resolve("file2"), "identity-file2 contents".getBytes());

    Path trustedAuthorityDir = securityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    Files.createDirectory(trustedAuthorityDir);
    Files.write(trustedAuthorityDir.resolve("file1"), "trusted-authority-file1 contents".getBytes());
    Files.write(trustedAuthorityDir.resolve("file2"), "trusted-authority-file2 contents".getBytes());

    serializeAndVerify(securityRootDirectory);
  }

  @Test
  public void testSerializationWithIdentityDirectoryOnly() throws Exception {
    Path securityRootDirectory = temporaryFolder.newFolder().toPath();

    Path identityDir = securityRootDirectory.resolve(IDENTITY_DIR_NAME);
    Files.createDirectory(identityDir);
    Files.write(identityDir.resolve("file1"), "identity-file1 contents".getBytes());
    Files.write(identityDir.resolve("file2"), "identity-file2 contents".getBytes());

    serializeAndVerify(securityRootDirectory);
  }

  @Test
  public void testSerializationWithEmptyIdentityDirectory() throws Exception {
    Path securityRootDirectory = temporaryFolder.newFolder().toPath();

    Path identityDir = securityRootDirectory.resolve(IDENTITY_DIR_NAME);
    Files.createDirectory(identityDir);

    Path trustedAuthorityDir = securityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    Files.createDirectory(trustedAuthorityDir);
    Files.write(trustedAuthorityDir.resolve("file1"), "trusted-authority-file1 contents".getBytes());
    Files.write(trustedAuthorityDir.resolve("file2"), "trusted-authority-file2 contents".getBytes());

    serializeAndVerify(securityRootDirectory);
  }

  @Test
  public void testSerializationWithTrustedAuthorityDirectoryOnly() throws Exception {
    Path securityRootDirectory = temporaryFolder.newFolder().toPath();

    Path trustedAuthorityDir = securityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    Files.createDirectory(trustedAuthorityDir);
    Files.write(trustedAuthorityDir.resolve("file1"), "trusted-authority-file1 contents".getBytes());
    Files.write(trustedAuthorityDir.resolve("file2"), "trusted-authority-file2 contents".getBytes());

    serializeAndVerify(securityRootDirectory);
  }

  @Test
  public void testSerializationWithEmptyTrustedAuthorityDirectory() throws Exception {
    Path securityRootDirectory = temporaryFolder.newFolder().toPath();

    Path identityDir = securityRootDirectory.resolve(IDENTITY_DIR_NAME);
    Files.createDirectory(identityDir);
    Files.write(identityDir.resolve("file1"), "identity-file1 contents".getBytes());
    Files.write(identityDir.resolve("file2"), "identity-file2 contents".getBytes());

    Path trustedAuthorityDir = securityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    Files.createDirectory(trustedAuthorityDir);

    serializeAndVerify(securityRootDirectory);
  }

  @Test
  public void testSerializationWithKeyStoreFiles() throws Exception {
    Path securityRootDirectory = Paths.get(getClass().getResource("/terracotta/10/security").toURI());
    serializeAndVerify(securityRootDirectory);
  }

  @Test
  public void testSerializationWithEmptyDirectory() throws Exception {
    Path securityRootDirectory = temporaryFolder.newFolder().toPath();
    serializeAndVerify(securityRootDirectory);
  }

  private void serializeAndVerify(Path actualSecurityRootDirectory) throws Exception {
    SecurityRootDirectory securityRootDirectory = SecurityRootDirectory.securityRootDirectory(actualSecurityRootDirectory.toUri().toURL());

    File serializedDataFile = temporaryFolder.newFile();
    FileOutputStream fileOutputStream = new FileOutputStream(serializedDataFile);
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
    objectOutputStream.writeObject(securityRootDirectory);

    FileInputStream fileInputStream = new FileInputStream(serializedDataFile);
    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
    SecurityRootDirectory deserializedSecurityRootDirectory = (SecurityRootDirectory) objectInputStream.readObject();

    Path deserializedSecurityRootDirectoryPath = temporaryFolder.newFolder().toPath();
    deserializedSecurityRootDirectory.createSecurityRootDirectory(deserializedSecurityRootDirectoryPath);

    verifyDirectories(actualSecurityRootDirectory, deserializedSecurityRootDirectoryPath);
  }

  private static void verifyDirectories(Path actualSecurityRootDirectory, Path deserializedSecurityRootDirectory) throws IOException {
    Path actualIdentityDir = actualSecurityRootDirectory.resolve(IDENTITY_DIR_NAME);
    Path actualTrustedAuthorityDir = actualSecurityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
    if (Files.exists(actualIdentityDir)) {
      final Path deserializedIdentityDir = deserializedSecurityRootDirectory.resolve(IDENTITY_DIR_NAME);
      assertThat(Files.exists(deserializedIdentityDir), is(true));
      verifyFiles(actualIdentityDir, deserializedIdentityDir);
    }
    if (Files.exists(actualTrustedAuthorityDir)) {
      final Path deserializedTrustedAuthorityDir = deserializedSecurityRootDirectory.resolve(TRUSTED_AUTHORITY_DIR_NAME);
      assertThat(Files.exists(actualTrustedAuthorityDir), is(true));
      verifyFiles(actualTrustedAuthorityDir, deserializedTrustedAuthorityDir);
    }
  }

  private static void verifyFiles(Path actualDirectory, Path deserializedDirectory) throws IOException {
    assertThat(Files.list(deserializedDirectory).count(), is(Files.list(actualDirectory).count()));
    Files.list(actualDirectory).forEach((actualPath -> {
      Path deserializedPath = deserializedDirectory.resolve(actualPath.getFileName());
      assertThat(Files.exists(deserializedPath), is(true));
      try {
        String expectedFileContents = IOUtils.toString(actualPath.toUri());
        String actualFileContents = IOUtils.toString(deserializedPath.toUri());
        assertThat(actualFileContents, is(expectedFileContents));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }));
  }
}