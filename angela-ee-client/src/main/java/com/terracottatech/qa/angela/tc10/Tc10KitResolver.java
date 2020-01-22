package com.terracottatech.qa.angela.tc10;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;

import com.terracottatech.qa.angela.KitResolver;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.DirectoryUtils;
import com.terracottatech.qa.angela.common.util.OS;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
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

import static com.terracottatech.qa.angela.common.topology.LicenseType.TERRACOTTA;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * @author Aurelien Broszniowski
 */

public class Tc10KitResolver extends KitResolver {

  private static final Logger logger = LoggerFactory.getLogger(Tc10KitResolver.class);

  @Override
  public String resolveLocalInstallerPath(Version version, LicenseType licenseType, PackageType packageType) {
    String fileName;
    if (packageType == KIT) {
      fileName = version.getMinor() > 5 || (version.getMinor() == 5 && version.getBuild_minor() >= 179) ? "terracotta-" : "terracotta-db-";
      fileName += version.getVersion(true) + ".tar.gz";
      return fileName;
    } else if (packageType == SAG_INSTALLER) {
      return getSAGInstallerName(version);
    }
    throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
  }

  /**
   * Software AG installer name
   *
   * @param version
   * @return
   */
  String getSAGInstallerName(Version version) {
    String versionValue = null;
    if (version.getMajor() >= 10) {
      if (version.getMinor() == 1) {
        versionValue = "101";
      } else if (version.getMinor() == 2) {
        versionValue = "102";
      } else if (version.getMinor() == 3) {
        if (version.getRevision() == 0) {
          versionValue = "103";
        } else if (version.getRevision() == 1) {
          versionValue = "104";
        }
      } else if (version.getMinor() == 5) {
        versionValue = "105";
      } else if (version.getMinor() == 7) {
        versionValue = "107";
      }
    } else {
      if (version.getMinor() == 3) {
        if (version.getRevision() == 0) {
          versionValue = "98";
        } else if (version.getRevision() == 1) {
          versionValue = "99";
        } else if (version.getRevision() == 2) {
          versionValue = "910";
        } else if (version.getRevision() == 3) {
          versionValue = "912";
        } else if (version.getRevision() == 4) {
          versionValue = "101";
        } else if (version.getRevision() == 5) {
          versionValue = "102";
        } else if (version.getRevision() == 6) {
          versionValue = "103";
        } else if (version.getRevision() == 7) {
          versionValue = "104";
        }
      }
    }

    if (versionValue == null) {
      throw new IllegalArgumentException("getSAGInstallerName couldn't resolve the name for version " + version.toString());
    }

    return "SoftwareAGInstaller" + versionValue + "_LATEST.jar";
  }


  @Override
  public void createLocalInstallFromInstaller(Version version, PackageType packageType, License license, Path localInstallerPath, Path rootInstallationPath) {
    if (packageType == KIT) {
      extract(localInstallerPath, rootInstallationPath);
    } else if (packageType == SAG_INSTALLER) {
      extractSag(getSandboxName(version), license, localInstallerPath, rootInstallationPath);
    } else {
      throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
    }
  }

  private String getSandboxName(Version version) {
    String sandbox = System.getProperty("sandbox");
    if (sandbox != null) {
      return sandbox;
    }

    if (version.getMajor() == 10) {
      if (version.getMinor() == 0) {
        return "dataservewebM100";
      } else if (version.getMinor() == 2) {
        return "dataservewebM102";
      } else if (version.getMinor() == 3) {
        return "dataservewebM103";
      } else if (version.getMinor() == 4) {
        return "dataservewebM104";
      } else if (version.getMinor() == 5) {
        return "dataservewebM105";
      } else if (version.getMinor() == 7) {
        return "dataservewebM107";
      }
    }
    throw new IllegalArgumentException("Missing Sandbox name : please pass -Dsandbox=");
  }

  private void extractSag(String sandboxName, License license, Path sagInstaller, Path dest) {
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

      if (OS.INSTANCE.isWindows()) {
        logger.info("Remove CCE service since it is not needed for testing");
        new ProcessExecutor()
            .command("sc", "delete", "sagcce")
            .redirectOutput(System.out)
            .execute();
        new ProcessExecutor()
            .command("sc", "delete", "sagcce104_6.1")
            .redirectOutput(System.out)
            .execute();
      }

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
    lines.add("ServerURL=http://aquarius_va.ame.ad.sag/cgi-bin/" + sandboxName + ".cgi");
    lines.add("sagInstallerLogFile=" + replaceWithDoubleSlashes(dest.resolve("installLog.txt").toString()));
    return lines;
  }

  private String replaceWithDoubleSlashes(String path) {
    //SAG installer script needs double backslashes to work correctly on Windows
    return OS.INSTANCE.isWindows() ? path.replace("\\", "\\\\") : path;
  }

  private void extract(Path kitInstaller, Path kitDest) {
    try {
      extractTarGz(kitInstaller, kitDest);
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new UncheckedIOException("Error when extracting installer package", ioe);
    }
    logger.info("kit installation path: {}", kitDest.toAbsolutePath());
  }

  // TODO : duplicate
  private void extractTarGz(Path kitInstaller, Path kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files
        .newInputStream(kitInstaller))))) {
      extractArchive(archiveIs, kitDest);
    }
    cleanupPermissions(kitDest);
  }

  // TODO : duplicate
  private void cleanupPermissions(Path dest) {
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

  // TODO : duplicate
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

  @Override
  public Path resolveKitInstallationPath(Version version, PackageType packageType, Path localInstallerPath, Path rootInstallationPath) {
    return rootInstallationPath.resolve(getDirFromArchive(packageType, version, localInstallerPath));
  }

  private String getDirFromArchive(PackageType packageType, Version version, Path localInstaller) {
    if (packageType == KIT) {
      return getParentDirFromTarGz(localInstaller);
    } else if (packageType == SAG_INSTALLER) {
      return "TDB";
    }
    throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
  }

  // TODO : duplicate
  private String getParentDirFromTarGz(Path localInstaller) {
    try (TarArchiveInputStream archiveIs = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(localInstaller)))) {
      ArchiveEntry entry = archiveIs.getNextEntry();
      return entry.getName().split("/")[0];
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  @Override
  public URL[] resolveKitUrls(Version version, LicenseType licenseType, PackageType packageType) {
    try {

      if (packageType == KIT) {
        String fullVersionString = version.getVersion(false);
        String pathMatch = "";

        String format = "http://kits.terracotta.eur.ad.sag:3000/release/download_latest?branch=%s&showrc=1&tag=%s&md5=%s&filename=%s";
        URL kitUrl = new URL(String.format(format, fullVersionString, licenseType.getKratosTag(), "false", pathMatch));
        URL md5Url = new URL(String.format(format, fullVersionString, licenseType.getKratosTag(), "true", pathMatch));
        return new URL[] { kitUrl, md5Url };
      } else if (packageType == SAG_INSTALLER) {
        URL kitUrl = new URL("http://aquarius_va.ame.ad.sag/PDShare/" + getSAGInstallerName(version));
        URL md5Url = null;
        return new URL[] { kitUrl, md5Url };
      } else {
        throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Can not resolve the url for the distribution package: " + packageType + ", " + licenseType + ", " + version, e);
    }
  }

  @Override
  public boolean supports(LicenseType licenseType) {
    return licenseType == TERRACOTTA;
  }
}
