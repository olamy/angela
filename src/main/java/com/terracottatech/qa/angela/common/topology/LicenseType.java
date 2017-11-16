package com.terracottatech.qa.angela.common.topology;

import java.io.Serializable;

/**
 * @author Aurelien Broszniowski
 */
public enum LicenseType implements Serializable {

  OS(true), TC_EHC(false), TC_DB(false),    // for 5.x
  GO(false), MAX(false);    // for 4.x

  private final boolean opensource;

  LicenseType(boolean opensource) {
    this.opensource = opensource;
  }

  public boolean isOpenSource() {
    return opensource;
  }
}
