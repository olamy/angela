package com.terracottatech.qa.angela.common.util;

import java.io.File;

public class DirectoryUtil {

  public static void createAndAssertDir(File dirToCreate, String qualifier) {
    if (!dirToCreate.exists()) {
      if (!dirToCreate.mkdirs()) {
        throw new RuntimeException("Auto creation of " + qualifier + " directory: " + dirToCreate + " failed. " +
            "Make sure that the provided directory is writable or create one manually.");
      }
    }
    if (!dirToCreate.isDirectory()) {
      throw new RuntimeException(qualifier + " is not a directory : " + dirToCreate);
    }
    if (!dirToCreate.canWrite()) {
      throw new RuntimeException(qualifier + " directory is not writable : " + dirToCreate);
    }
    dirToCreate.setWritable(true, false);
  }

}
