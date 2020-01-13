package com.terracottatech.qa.angela.common.tcconfig;

import java.util.Objects;

/**
 * @author Ludovic Orban
 */
public final class ServerSymbolicName {

  private final String symbolicName;

  public ServerSymbolicName(String symbolicName) {
    Objects.requireNonNull(symbolicName, "symbolicName cannot be null");
    this.symbolicName = symbolicName;
  }

  public static ServerSymbolicName symbolicName(String name) {
    return new ServerSymbolicName(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ServerSymbolicName that = (ServerSymbolicName) o;

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
    return "ServerSymbolicName{" + symbolicName + '}';
  }
}
