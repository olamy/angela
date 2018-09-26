package com.terracottatech.qa.angela.common.tcconfig;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

/**
 * Created by esebasti on 7/21/17.
 */
public class License {

  private final String licenseContent;
  private final String filename;

  public License(URL licensePath) {
    this.filename = new File(licensePath.getFile()).getName();
    try (InputStream is = licensePath.openStream()) {
      if (is == null) {
        throw new IllegalArgumentException("License file is not present");
      }
      licenseContent = IOUtils.toString(is);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public File writeToFile(File dest) {
    File licenseFile = new File(dest, this.filename);
    try {
      Files.write(licenseFile.toPath(), licenseContent.getBytes());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return licenseFile;
  }

  public String getFilename() {
    return filename;
  }
}
