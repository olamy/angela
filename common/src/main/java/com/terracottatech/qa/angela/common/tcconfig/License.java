package com.terracottatech.qa.angela.common.tcconfig;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;

/**
 * Created by esebasti on 7/21/17.
 */
public class License implements Serializable {

  private final String licenseContent;

  public License(URL licensePath) {
    try (InputStream is = licensePath.openStream()) {
      if (is == null) {
        throw new IllegalArgumentException("License file is not present");
      }
      licenseContent = IOUtils.toString(is);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }


  public void WriteToFile(File file) {
    try (FileOutputStream fos = new FileOutputStream(file)) {
      IOUtils.write(licenseContent, fos);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
}
