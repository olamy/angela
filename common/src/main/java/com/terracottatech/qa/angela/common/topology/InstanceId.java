package com.terracottatech.qa.angela.common.topology;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Aurelien Broszniowski
 */

public class InstanceId {
  private final String prefix;
  private final UUID uuid;

  public InstanceId(final Topology topology) {
    Objects.requireNonNull(topology);
    this.prefix = Objects.requireNonNull(topology.getId()).replaceAll("[^a-zA-Z0-9.-]", "_");;
    this.uuid = UUID.randomUUID();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final InstanceId that = (InstanceId)o;

    if (!prefix.equals(that.prefix)) return false;
    return uuid.equals(that.uuid);
  }

  @Override
  public int hashCode() {
    int result = prefix.hashCode();
    result = 31 * result + uuid.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return prefix + "-" + uuid.toString();
  }

}
