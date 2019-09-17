package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.Version;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class CompressionUtils {

  private static final Logger logger = LoggerFactory.getLogger(CompressionUtils.class);

  public CompressionUtils() {
  }

  public void extract(File kitInstaller, File kitDest) {
    try {
      if (kitInstaller.getName().endsWith("tar.gz")) {
        extractTarGz(kitInstaller, kitDest);
      } else if (kitInstaller.getName().endsWith(".zip")) {
        extractZip(kitInstaller, kitDest);
      } else {
        throw new RuntimeException("Installer format of file [" + kitInstaller.getName() + "] is not supported");
      }
    } catch (IOException ioe) {
      throw new UncheckedIOException("Error when extracting installer package", ioe);
    }
    logger.info("kit installation path = {}", kitDest.getAbsolutePath());
  }

  private void extractTarGz(File kitInstaller, File kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(kitInstaller))))) {
      extractArchive(archiveIs, kitDest.toPath());
    }
    cleanupPermissions(kitDest);
  }

  private void extractZip(final File kitInstaller, final File kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(new FileInputStream(kitInstaller)))) {
      extractArchive(archiveIs, kitDest.toPath());
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

  public String getParentDirFromTarGz(final File localInstaller) {
    try (TarArchiveInputStream archiveIs = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public String getParentDirFromZip(final File localInstaller) {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(new FileInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public void cleanupPermissions(File dest) {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return;
    }

    try (Stream<Path> walk = Files.walk(dest.toPath())) {
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

  public void extractSag(String sandboxName, final Version version, License license, final File sagInstaller, final File dest) {
    final File localInstallDir = new File(dest.getPath() + File.separatorChar + "TDB");
    // create console script
    File scriptFile = new File(dest.getPath() + File.separatorChar + "install-script.txt");
    scriptFile.delete();

    File licenseFile = license.writeToFile(new File(dest.getPath()));

    int exit;
    try {
      PrintWriter writer = new PrintWriter(scriptFile, "UTF-8");
      writer.println("Username=latest");
      writer.println("Password=latest");
      writer.println("sagInstallerLogLevel=verbose");
      writer.println("LicenseAgree=Accept");
      writer.println("InstallProducts=" +
          "e2ei/11/SJP_.LATEST/Infrastructure/sjp," +
          "e2ei/11/TDB_.LATEST/TDB/TDBServer," +
          "e2ei/11/TDB_.LATEST/TDB/TDBStore," +
          "e2ei/11/TDB_.LATEST/TDB/TDBEhcache," +
          "e2ei/11/TDB_.LATEST/TDB/TDBCommon," +
          "e2ei/11/TDB_.LATEST/TDB/TDBConsole," +
          "e2ei/11/TDB_.LATEST/TDB/TDBCluster," +
          "e2ei/11/TPL_.LATEST/License/license");
      writer.println("InstallDir=" + localInstallDir.getPath());
      writer.println("TDB.licenseAll=__VERSION1__," + licenseFile.getPath());
      writer.println("ServerURL=http://aquarius_va.ame.ad.sag/cgi-bin/dataserve" + sandboxName + ".cgi");
      writer.println("sagInstallerLogFile=" + dest + "/installLog.txt");
      writer.close();

      OutputStream out = new ByteArrayOutputStream();
      exit = new ProcessExecutor().command("java", "-jar", sagInstaller.getPath(), "-readScript", scriptFile.getPath(), "-console")
          .redirectOutput(out)
          .execute().getExitValue();
      logger.info(out.toString());
      logger.info("local kit installation path = {}", localInstallDir.getAbsolutePath());
    } catch (Exception e) {
      try {
        Files.walk(localInstallDir.toPath())
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(File::delete);
      } catch (IOException e1) {
        logger.error("Error when cleaning kit installation {} after a SAG installer execution error", localInstallDir.toPath(), e1);
      }
      throw new RuntimeException("Problem when installing Terracotta from SAG installer script " + scriptFile.getPath());
    }
    if (exit != 0) {
      try {
        Files.walk(localInstallDir.toPath())
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(File::delete);
      } catch (IOException e1) {
        logger.error("Error when cleaning kit installation {} after a SAG installer returned a failure exit code", localInstallDir.toPath(), e1);
      }
      throw new RuntimeException("Error when installing with the sag installer. Check the file " + dest.getPath() + File.separatorChar + "installLog.txt");
    }
  }
}
