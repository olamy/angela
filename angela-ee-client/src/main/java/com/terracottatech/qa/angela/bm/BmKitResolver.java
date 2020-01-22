package com.terracottatech.qa.angela.bm;

import io.restassured.path.json.JsonPath;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.terracottatech.qa.angela.common.topology.LicenseType.GO;
import static com.terracottatech.qa.angela.common.topology.PackageType.KIT;
import static com.terracottatech.qa.angela.common.topology.PackageType.SAG_INSTALLER;

/**
 * @author Aurelien Broszniowski
 */

public class BmKitResolver extends KitResolver {

  private static final Logger logger = LoggerFactory.getLogger(BmKitResolver.class);

  @Override
  public String resolveLocalInstallerPath(Version version, LicenseType licenseType, PackageType packageType) {
    if (packageType == KIT) {
      return "bigmemory-" + (licenseType == GO ? "go-" : "max-") + version.getVersion(true) + ".tar.gz";
    } else if (packageType == SAG_INSTALLER) {
      return getSAGInstallerName(version);
    }
    throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
  }

  private String getSAGInstallerName(Version version) {
    return "SoftwareAGInstaller107_LATEST.jar";
  }

  @Override
  public void createLocalInstallFromInstaller(Version version, PackageType packageType, License license, Path localInstallerPath, Path rootInstallationPath) {
    if (packageType == KIT) {
      extract(localInstallerPath, rootInstallationPath);
    } else if (packageType == SAG_INSTALLER) {
      extractSag(getSandboxName(version), license, localInstallerPath, rootInstallationPath);
      if (version.getBuild_major() > 0) {
        installSumFix(version, localInstallerPath.getParent(), rootInstallationPath);
      }
      shutdownSPM(rootInstallationPath);
    } else {
      throw new IllegalArgumentException("PackageType " + packageType + " is not supported by " + getClass().getSimpleName());
    }
  }

  private void installSumFix(Version version, Path localinstallDirPath, Path dest) {
    try {
      installSUM(version, localinstallDirPath, dest);
    } catch (IOException e) {
      throw new RuntimeException("Couldn't install SUM", e);
    }

    installFixes(version, localinstallDirPath, dest);
  }

  private void shutdownSPM(Path dest) {
    Path localInstallDir = dest.resolve("BM");
    final AtomicReference<String> fullSpmPath = new AtomicReference<>();
    logger.info("shutting down CCE/SPM which was started during fix install");
    String spmCCEShutdownScript;
    if (OS.INSTANCE.isWindows()) {
      spmCCEShutdownScript = "shutdown.bat";
    } else {
      spmCCEShutdownScript = "shutdown.sh";
    }

    Stream.of("profiles/SPM/bin/", "profiles/CCE/bin/").forEach(exe -> {
      fullSpmPath.set(localInstallDir.resolve(exe).resolve(spmCCEShutdownScript).toFile().getAbsolutePath());
      try {
        new ProcessExecutor(fullSpmPath.get())
            .redirectOutput(System.out)
            .execute();
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Problem when stopping SPM " + fullSpmPath.get(), e);
      }
    });
  }

  private void installFixes(Version version, Path localinstallDirPath, Path dest) {
    Path localInstallDir = dest.resolve("BM");
    Path scriptFile = dest.resolve("fix_script.txt");
    int exit = -1;
    try {
      List<String> lines = createFixesInstallerScript(version, dest, localInstallDir);
      Files.write(scriptFile, lines);

      List<String> repos = new ArrayList<>();
      if (!getAllGAFixVersions(version).isEmpty()) {
        repos.add("GA_Fix_Repo");
      }
      repos.add("QARepo");

      for (String repo : repos) {
        String sumExec;
        if (OS.INSTANCE.isWindows()) {
          sumExec = "UpdateManagerCMD.bat";
        } else {
          sumExec = "UpdateManagerCMD.sh";
        }
        if (version.getShortVersion().equals("4.3.2") || version.getShortVersion().equals("4.3.3")) {
          sumExec = "sag/UpdateManager/bin/" + sumExec;
        } else {
          sumExec = "sum/bin/" + sumExec;
        }
        final Path sumExecFile = dest.resolve(sumExec);

        exit = new ProcessExecutor()
            .command(sumExecFile.toAbsolutePath().toString(), "-server", "aquarius-va.ame.ad.sag:" + repo, "-readScript", scriptFile.toString())
            .redirectOutput(System.out)
            .execute()
            .getExitValue();
        logger.info("local kit installation path: {}", localInstallDir.toAbsolutePath());
      }


    } catch (Exception e) {
      if (!DirectoryUtils.deleteQuietly(localInstallDir)) {
        logger.error("Error when cleaning kit installation {} after a SUM execution error", localInstallDir);
      }
      e.printStackTrace();
      throw new RuntimeException("Problem when installing Terracotta fixes from SUM script " + scriptFile);
    }

  }

  List<String> getAllGAFixVersions(Version version) throws IOException {
    URL url = new URL("http://aquarius-va.ame.ad.sag:8088" + "/reposervice/mainservlet?functionName=listFixes&repoName=GA_Fix_Repo&queryKeyword=wMFix.TES_" + version
        .getShortVersion());

    HttpURLConnection connection;
    connection = (HttpURLConnection)url.openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Content-Type", "application/json; utf-8");
    String encoded = Base64.getEncoder().encodeToString("repo:repo".getBytes(StandardCharsets.UTF_8));
    connection.setRequestProperty("Authorization", "Basic " + encoded);

    connection.setRequestProperty("Accept", "application/json");
    connection.setDoOutput(true);
    connection.connect();
    BufferedReader in = new BufferedReader(
        new InputStreamReader(connection.getInputStream()));
    String inputLine;
    StringBuilder response = new StringBuilder();
    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();
    List<String> versions = JsonPath.with(response.toString()).get("value.fixes.fixVersion");
    Collections.sort(versions);
    return versions;
  }

  private List<String> createFixesInstallerScript(Version version, Path dest, Path localInstallDir) {
    List<String> lines = new ArrayList<>();
    lines.add("combooption=Fix Management");
    lines.add("empowerUser=sum");
    lines.add("diagFile=");
    lines.add("action=Install fixes from Empower");
    final String shortVersion = version.getShortVersion();
    lines.add("selectedFixes=" + "wMFix.TES_" + shortVersion + ",wMFix.TESCommon_" + shortVersion +
              ",wMFix.TESOSGi_" + shortVersion + ",wMFix.TESDES_" + shortVersion);
    lines.add("empowerPwd=3umM+tH0xBw\\=");
    lines.add("diagLevel=LOW");
    lines.add("installDir=" + replaceWithDoubleSlashes(localInstallDir.toString()));
    lines.add("diagnoserKey=");
    lines.add("createEmpowerImage=L");
    lines.add("imageFile=");
    lines.add("pivotalid=");
    return lines;
  }

  // Install the Software AG Update Manager
  private void installSUM(Version version, Path localinstallDirPath, Path dest) throws IOException {
    if (version.getMajor() == 4 && version.getMinor() == 3 && version.getRevision() <= 3) {
      // SUM is included with the SAG installer in older versions
      return;
    }

    final Path localSumBootInstall = localinstallDirPath.resolve("sum_boot");
    Files.createDirectory(localSumBootInstall);
    final Path localSumInstall = localinstallDirPath.resolve("sum");
    Files.createDirectory(localSumInstall);

    int exit;
    try {
      if (OS.INSTANCE.isWindows()) {
        final String sumName = "SAGUpdateManagerInstaller-windows-x64-10.0.0.0000-0036.zip";
        final URL sum = new URL("http://aquarius-va.ame.ad.sag:8092/sumv2/bootstrapper/windows-x64/" + sumName);
        download(sum, localSumBootInstall.resolve(sumName));
        extractZip(localSumBootInstall.resolve(sumName), localSumBootInstall);
        exit = new ProcessExecutor()
            .command(localSumBootInstall.resolve("sum-setup.bat").toAbsolutePath().toString(),
                "--accept-license", "-d", localSumInstall.toAbsolutePath().toString())
            .redirectOutput(System.out)
            .execute()
            .getExitValue();
      } else if (OS.INSTANCE.isPosix()) {
        final String sumName = "SAGUpdateManagerInstaller-all-10.0.0.0000-0036.sh";
        URL sum = new URL("http://aquarius-va.ame.ad.sag:8092/sumv2/bootstrapper/all/" + sumName);
        download(sum, localSumBootInstall.resolve(sumName));
        cleanupPermissions(localSumBootInstall);
        exit = new ProcessExecutor()
            .command(localSumBootInstall.resolve(sumName).toAbsolutePath().toString(),
                "--accept-license", "-d", localSumInstall.toAbsolutePath().toString())
            .redirectOutput(System.out)
            .execute()
            .getExitValue();

      } else {
        throw new UnsupportedOperationException("Current Os " + OS.INSTANCE.toString() + " doesn't support SUM");
      }
      if (exit != 0) {
        throw new RuntimeException("Error when installing with the sag update manager.");
      }
    } catch (InterruptedException | TimeoutException e) {
      throw new RuntimeException("Error when installing with the sag update manager.");
    }
  }

  private String getSandboxName(Version version) {
    String sandbox = System.getProperty("sandbox");
    if (sandbox != null) {
      return sandbox;
    }

    if (version.getMajor() == 4) {
      if (version.getMinor() == 3) {
        if (version.getRevision() == 2) {
          return "dataservewebM910";
        } else if (version.getRevision() == 3) {
          return "dataservewebM912";
        } else if (version.getRevision() == 4) {
          return "dataservewebM100";
        } else if (version.getRevision() == 5) {
          return "dataservewebM102";
        } else if (version.getRevision() == 6) {
          return "dataservewebM103";
        } else if (version.getRevision() == 7) {
          return "dataservewebM104";
        } else if (version.getRevision() == 8) {
          return "dataservewebM105";
        } else if (version.getRevision() == 9) {
          return "dataservewebM107";
        }
      }
    }
    throw new IllegalArgumentException("Missing Sandbox name : please pass -Dsandbox=");
  }

  // TODO : duplicate
  private void extractSag(String sandboxName, License license, Path sagInstaller, Path dest) {
    Path localInstallDir = null;
    Path scriptFile = null;
    try {
      localInstallDir = dest.resolve("BM");
      Files.createDirectories(localInstallDir);
      scriptFile = dest.resolve("install-script.txt");
    } catch (IOException e) {
      throw new RuntimeException("Error when installing BigMemory, directory can't be created", e);
    }

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
    lines.add("CCEHttpPort=__VERSION1__,8090");
    lines.add("CCEHttpsPort=__VERSION1__,8091");
    lines.add("InstallProducts=" +
              "e2ei/11/CCE_.LATEST/Platform/CCE," +
              "e2ei/11/TES_.LATEST/TES/TES," +
              "e2ei/11/TES_SPM_.LATEST/TES/TESspm," +
              "e2ei/11/TES_.LATEST/DES/TESDES," +
              "e2ei/11/SPM_.LATEST/Platform/SPM," +
              "e2ei/11/TES_.LATEST/TES/TESOSGi," +
              "e2ei/11/TES_.LATEST/TES/TESCommon");
    lines.add("InstallDir=" + replaceWithDoubleSlashes(localInstallDir.toString()));
    lines.add("LicenseAgree=Accept");
    lines.add("SPMHttpsPort=__VERSION1__,8093");
    lines.add("TES.LicenseFile.text=__VERSION1__," + replaceWithDoubleSlashes(license.writeToFile(dest.toFile())
        .getPath()));
    lines.add("ServerURL=http://aquarius_va.ame.ad.sag/cgi-bin/" + sandboxName + ".cgi");
    lines.add("SPMHttpPort=__VERSION1__,8092");
    lines.add("AcceptInnovationRelease=yes");
    lines.add("sagInstallerLogFile=" + replaceWithDoubleSlashes(dest.resolve("installLog.txt").toString()));
    return lines;
  }

  // TODO : duplicate
  private String replaceWithDoubleSlashes(String path) {
    //SAG installer script needs double backslashes to work correctly on Windows
    return OS.INSTANCE.isWindows() ? path.replace("\\", "\\\\") : path;
  }

  private void extract(Path kitInstaller, Path kitDest) {
    try {
      extractTarGz(kitInstaller, kitDest);
    } catch (IOException ioe) {
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
  private void extractZip(Path kitInstaller, Path kitDest) throws IOException {
    try (ArchiveInputStream archiveIs = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(kitInstaller)))) {
      extractArchive(archiveIs, kitDest);
    }
    cleanupPermissions(kitDest);
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
      return "BM";
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
        if (version.isSnapshot()) {
          fullVersionString = "trunk";
          pathMatch = version.getVersion(false); //we'll have to restrict by filename
        }

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
    return licenseType == LicenseType.GO || licenseType == LicenseType.MAX;
  }
}
