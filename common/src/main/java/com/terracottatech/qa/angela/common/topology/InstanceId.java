package com.terracottatech.qa.angela.common.topology;

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class InstanceId {
  private final String prefix;
  private final String type;

  public InstanceId(String idPrefix, String type) {
    this.prefix = Objects.requireNonNull(idPrefix).replaceAll("[^a-zA-Z0-9.-]", "_");;
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InstanceId that = (InstanceId) o;
    return Objects.equals(prefix, that.prefix) &&
        Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefix, type);
  }

  @Override
  public String toString() {
    return String.format("%s-%s", prefix, type);
  }

}
