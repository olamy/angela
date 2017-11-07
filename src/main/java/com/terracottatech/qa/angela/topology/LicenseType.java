package com.terracottatech.qa.angela.topology;

import java.io.Serializable;

/**
 * @author Aurelien Broszniowski
 */
public enum LicenseType implements Serializable {

  OS, TC_EHC, TC_DB,    // for 5.x
  GO, MAX;    // for 4.x

  LicenseType() {
  }
}
