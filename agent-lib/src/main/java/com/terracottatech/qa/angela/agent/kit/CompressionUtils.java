package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.util.DirectoryUtils;
import com.terracottatech.qa.angela.common.util.OS;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class CompressionUtils {
  private static final Logger logger = LoggerFactory.getLogger(CompressionUtils.class);

  public void extract(Path kitInstaller, Path kitDest) {
    try {
      String fileName = kitInstaller.getFileName().toString();
      if (fileName.endsWith(".tar.gz")) {
        extractTarGz(kitInstaller, kitDest);
      } else if (fileName.endsWith(".zip")) {
        extractZip(kitInstaller, kitDest);
      } else {
        throw new RuntimeException("Installer format of file: " + fileName + " is not supported");
      }
    } catch (IOException ioe) {
      throw new UncheckedIOException("Error when extracting installer package", ioe);
    }
    logger.info("kit installation path: {}", kitDest.toAbsolutePath());
  }

  private void extractTarGz(Path kitInstaller, Path kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(kitInstaller))))) {
      extractArchive(archiveIs, kitDest);
    }
    cleanupPermissions(kitDest);
  }

  private void extractZip(Path kitInstaller, Path kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(kitInstaller)))) {
      extractArchive(archiveIs, kitDest);
    }
    cleanupPermissions(kitDest);
  }

  private void extractArchive(ArchiveInputStream archiveIs, Path pathOutput) throws IOException {
    while (true) {
      ArchiveEntry archiveEntry = archiveIs.getNextEntry();
      if (archiveEntry == null) {
        break;
      }

      Path pathEntryOutput = pathOutput.resolve(archiveEntry.getName());
      if (!archiveEntry.isDirectory()) {
        Path parentPath = pathEntryOutput.getParent();
        if (!Files.isDirectory(parentPath)) {
          Files.createDirectories(parentPath);
        }
        Files.copy(archiveIs, pathEntryOutput);
      }
    }
  }

  public String getParentDirFromTarGz(Path localInstaller) {
    try (TarArchiveInputStream archiveIs = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public String getParentDirFromZip(Path localInstaller) {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public void cleanupPermissions(Path dest) {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return;
    }

    try (Stream<Path> walk = Files.walk(dest)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".sh") || name.endsWith("tms.jar");
          })
          .forEach(path -> {
            try {
              Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(path));
              perms.addAll(EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE));
              Files.setPosixFilePermissions(path, perms);
            } catch (IOException ioe) {
              throw new UncheckedIOException(ioe);
            }
          });
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public void extractSag(String sandboxName, License license, Path sagInstaller, Path dest) {
    Path localInstallDir = dest.resolve("TDB");
    Path scriptFile = dest.resolve("install-script.txt");

    int exit;
    try {
      List<String> lines = createInstallerScript(sandboxName, license, dest, localInstallDir);
      Files.write(scriptFile, lines);

      exit = new ProcessExecutor()
          .command("java", "-jar", sagInstaller.toString(), "-readScript", scriptFile.toString(), "-console")
          .redirectOutput(System.out)
          .execute()
          .getExitValue();
      logger.info("local kit installation path: {}", localInstallDir.toAbsolutePath());
    } catch (Exception e) {
      if (!DirectoryUtils.deleteQuietly(localInstallDir)) {
        logger.error("Error when cleaning kit installation {} after a SAG installer execution error", localInstallDir);
      }
      throw new RuntimeException("Problem when installing Terracotta from SAG installer script " + scriptFile);
    }
    if (exit != 0) {
      if (!DirectoryUtils.deleteQuietly(localInstallDir)) {
        logger.error("Error when cleaning kit installation {} after a SAG installer returned a failure exit code", localInstallDir);
      }
      throw new RuntimeException("Error when installing with the sag installer. Check the file " + dest.resolve("installLog.txt"));
    }
  }

  private List<String> createInstallerScript(String sandboxName, License license, Path dest, Path localInstallDir) {
    List<String> lines = new ArrayList<>();
    lines.add("Username=latest");
    lines.add("Password=latest");
    lines.add("sagInstallerLogLevel=verbose");
    lines.add("LicenseAgree=Accept");
    lines.add("InstallProducts=" +
        "e2ei/11/SJP_.LATEST/Infrastructure/sjp," +
        "e2ei/11/TDB_.LATEST/TDB/TDBServer," +
        "e2ei/11/TDB_.LATEST/TDB/TDBStore," +
        "e2ei/11/TDB_.LATEST/TDB/TDBEhcache," +
        "e2ei/11/TDB_.LATEST/TDB/TDBCommon," +
        "e2ei/11/TDB_.LATEST/TDB/TDBConsole," +
        "e2ei/11/TDB_.LATEST/TDB/TDBCluster," +
        "e2ei/11/TPL_.LATEST/License/license");
    lines.add("InstallDir=" + replaceWithDoubleSlashes(localInstallDir.toString()));
    lines.add("TDB.licenseAll=__VERSION1__," + replaceWithDoubleSlashes(license.writeToFile(dest.toFile()).getPath()));
    lines.add("ServerURL=http://aquarius_va.ame.ad.sag/cgi-bin/dataserve" + sandboxName + ".cgi");
    lines.add("sagInstallerLogFile=" + replaceWithDoubleSlashes(dest.resolve("installLog.txt").toString()));
    return lines;
  }

  private String replaceWithDoubleSlashes(String path) {
    //SAG installer script needs double backslashes to work correctly on Windows
    return OS.INSTANCE.isWindows() ? path.replace("\\", "\\\\") : path;
  }
}
