package com.terracottatech.qa.angela.common.clientconfig;

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class ClientSymbolicName {

  private final String symbolicName;

  public ClientSymbolicName(final String symbolicName) {
    Objects.requireNonNull(symbolicName, "symbolicName cannot be null");
    this.symbolicName = symbolicName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClientSymbolicName that = (ClientSymbolicName) o;

    return symbolicName.equals(that.symbolicName);
  }

  @Override
  public int hashCode() {
    return symbolicName.hashCode();
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  @Override
  public String toString() {
    return "ClientSymbolicName{" + symbolicName + '}';
  }

}
