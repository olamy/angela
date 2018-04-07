package com.terracottatech.qa.angela.agent.kit;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.taskdefs.ExecuteOn;
import org.apache.tools.ant.taskdefs.Untar;
import org.apache.tools.ant.types.FileSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.zip.ZipUtil;

import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.topology.Version;
import com.terracottatech.qa.angela.common.util.OS;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CompressionUtils implements Serializable {

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
        throw new RuntimeException("Installer format not recognized.");
      }
    } catch (Exception e) {
      throw new RuntimeException("Error when extracting installer package", e);
    }
    logger.info("kit installation path = {}", kitDest.getAbsolutePath());
  }

  private void extractTarGz(File kitInstaller, File kitDest) throws IOException, TimeoutException, InterruptedException {
    if (OS.INSTANCE.isWindows()) {
      extractTarGzJava(kitInstaller, kitDest);
    } else {
      extractTarGzCmd(kitInstaller, kitDest);
    }
  }

  private void extractTarGzJava(File kitInstaller, File kitDest) throws IOException {
    Project project = new Project();
    Untar untar = new Untar();
    untar.setProject(project);
    Untar.UntarCompressionMethod method = new Untar.UntarCompressionMethod();
    method.setValue("gzip");
    untar.setCompression(method);
    untar.setDest(kitDest);
    untar.setSrc(kitInstaller);
    untar.execute();

    cleanupPermissions(kitDest);
  }

  private void extractTarGzCmd(final File kitInstaller, final File kitDest) throws InterruptedException, TimeoutException, IOException {
    OutputStream out = new ByteArrayOutputStream();
    new ProcessExecutor().command("tar", "xzvf", kitInstaller.getPath()).directory(kitDest)
        .redirectOutput(out)
        .execute();
  }

  private void extractTarGz2(final File kitInstaller, final File kitDest) throws IOException {
    kitDest.mkdir();
    TarArchiveInputStream tarIn;

    tarIn = new TarArchiveInputStream(
        new GzipCompressorInputStream(
            new BufferedInputStream(
                new FileInputStream(
                    kitInstaller
                )
            )
        )
    );

    TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
    // tarIn is a TarArchiveInputStream
    while (tarEntry != null) {// create a file with the same name as the tarEntry
      File destPath = new File(kitDest, tarEntry.getName());
      System.out.println("working: " + destPath.getCanonicalPath());
      if (tarEntry.isDirectory()) {
        destPath.mkdirs();
      } else {
        destPath.createNewFile();
        //byte [] btoRead = new byte[(int)tarEntry.getSize()];
        byte[] btoRead = new byte[1024];
        //FileInputStream fin
        //  = new FileInputStream(destPath.getCanonicalPath());
        BufferedOutputStream bout =
            new BufferedOutputStream(new FileOutputStream(destPath));
        int len = 0;

        while ((len = tarIn.read(btoRead)) != -1) {
          bout.write(btoRead, 0, len);
        }

        bout.close();
        btoRead = null;

      }
      tarEntry = tarIn.getNextTarEntry();
    }
    tarIn.close();
    System.out.println("untar completed successfully!!");
  }

  public String getParentDirFromTarGz(final File localInstaller) throws IOException {
    FileInputStream fileInputStream = new FileInputStream(localInstaller);
    GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream);
    TarArchiveEntry tarArchiveEntry;
    tarArchiveEntry = tarArchiveInputStream.getNextTarEntry();
    String[] split = tarArchiveEntry.getName().split(Pattern.quote(File.separator));
    return split[0];
  }


  private void extractZip(final File kitInstaller, final File kitDest) throws IOException, ArchiveException, TimeoutException, InterruptedException {
    if (OS.INSTANCE.isWindows()) {
      extractZipJava(kitInstaller, kitDest);
    } else {
      extractZipCmd(kitInstaller, kitDest);
    }
  }

  private void extractZipJava(final File kitInstaller, final File kitDest) throws IOException {
    ZipUtil.unpack(kitInstaller, kitDest);
    cleanupPermissions(kitDest);
  }

  private void extractZipCmd(final File kitInstaller, final File kitDest) throws InterruptedException, TimeoutException, IOException {
    OutputStream out = new ByteArrayOutputStream();
    new ProcessExecutor().command("unzip", "-o", kitInstaller.getPath(), "-d", kitDest.getPath())
        .redirectOutput(out)
        .execute();
    System.out.println(out.toString());
  }

  public void cleanupPermissions(File dest) throws IOException {
    Chmod chmod = new Chmod();
    chmod.setProject(new Project());
    chmod.setPerm("ugo+x");
    ExecuteOn.FileDirBoth fdb = new ExecuteOn.FileDirBoth();
    fdb.setValue("file");
    chmod.setType(fdb);

    FileSet fileSet = new FileSet();
    fileSet.setDir(dest);
    fileSet.setIncludes("**/*.sh");
    chmod.addFileset(fileSet);

    fileSet = new FileSet();
    fileSet.setDir(dest);
    fileSet.setIncludes("**/*.bat");
    chmod.addFileset(fileSet);

    fileSet = new FileSet();
    fileSet.setDir(dest);
    fileSet.setIncludes("**/tms.jar");
    chmod.addFileset(fileSet);

    chmod.execute();
  }

  private void extractZip2(final File kitInstaller, final File kitDest) throws IOException, ArchiveException {
    // create the input stream for the file, then the input stream for the actual zip file
    final InputStream is = new FileInputStream(kitInstaller);
    ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, is);

    // cycle through the entries in the zip archive and write them to the system temp dir
    ZipArchiveEntry entry = (ZipArchiveEntry)ais.getNextEntry();
    while (entry != null) {
      File outputFile = new File(kitDest, entry.getName());

      OutputStream os = new FileOutputStream(outputFile);

      IOUtils.copy(ais, os);  // copy from the archiveinputstream to the output stream
      os.close();     // close the output stream

      entry = (ZipArchiveEntry)ais.getNextEntry();
    }

    ais.close();
    is.close();
  }

  public String getParentDirFromZip(final File localInstaller) throws ArchiveException, IOException {
    final InputStream is = new FileInputStream(localInstaller);
    ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, is);
    ZipArchiveEntry entry = (ZipArchiveEntry)ais.getNextEntry();
    String[] entryPieces = entry.getName().split("\\/");
    return entryPieces[0];
  }

  public synchronized byte[] zipAsByteArray(final File dirToBeCompressed) {
    byte[] bytesArray = null;
    try {
      File zipFileName = File.createTempFile("temp", Long.toString(System.nanoTime()));
      ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));
      addDir(dirToBeCompressed, out);
      out.close();

      bytesArray = new byte[(int)zipFileName.length()];

      FileInputStream fis = new FileInputStream(zipFileName);
      fis.read(bytesArray);
      fis.close();
    } catch (Exception e) {
      throw new RuntimeException("Can not zip server logs results", e);
    }
    return bytesArray;
  }

  public synchronized void byteArrayToZip(final File location, byte[] bytesArray) {
    try {
      FileOutputStream fos = new FileOutputStream(location);
      fos.write(bytesArray);
      fos.close();
    } catch (Exception e) {
      throw new RuntimeException("Can not write server logs results", e);
    }
  }

  private void addDir(File dirObj, ZipOutputStream out) throws IOException {
    File[] files = dirObj.listFiles();
    byte[] tmpBuf = new byte[1024];

    if (files != null) {
      for (final File file : files) {
        if (file.isDirectory()) {
          addDir(file, out);
          continue;
        }
        FileInputStream in = new FileInputStream(file.getAbsolutePath());
        System.out.println(" Adding: " + file.getAbsolutePath());
        out.putNextEntry(new ZipEntry(file.getAbsolutePath()));
        int len;
        while ((len = in.read(tmpBuf)) > 0) {
          out.write(tmpBuf, 0, len);
        }
        out.closeEntry();
        in.close();
      }
    }
  }

  public void extractSag(final Version version, License license, final File sagInstaller, final File dest) {
    final File localInstallDir = new File(dest.getPath() + File.separatorChar + "TDB");
    // create console script
    File scriptFile = new File(dest.getPath() + File.separatorChar + "install-script.txt");
    scriptFile.delete();

    File licenseFile = new File(dest.getPath() + File.separatorChar + "license.xml");
    license.writeToFile(licenseFile);

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
      writer.println("ServerURL=http://aquarius_va.ame.ad.sag/cgi-bin/dataserve" + getSandboxName(version) + ".cgi");
      writer.println("sagInstallerLogFile=" + dest + "/installLog.txt");
      writer.close();

      OutputStream out = new ByteArrayOutputStream();
      new ProcessExecutor().command("java", "-jar", sagInstaller.getPath(), "-readScript", scriptFile.getPath(), "-console")
          .redirectOutput(out)
          .execute();
      System.out.println(out.toString());
      logger.info("kit installation path = {}", localInstallDir.getAbsolutePath());
    } catch (Exception e) {
      throw new RuntimeException("Problem when installing Terracotta from SAG installer script " + scriptFile.getPath());
    }
  }

  private String getSandboxName(final Version version) {
    String sandbox = System.getProperty("sandbox");
    if (sandbox != null) {
      return sandbox;
    }
    throw new IllegalArgumentException("Missing Sandbox name : please pass -Dsandbox=");
  }
}
