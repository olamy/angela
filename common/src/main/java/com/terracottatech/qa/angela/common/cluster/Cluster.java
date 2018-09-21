package com.terracottatech.qa.angela.common.cluster;

import org.apache.ignite.Ignite;

public class Cluster {
  private final Ignite ignite;

  public Cluster(Ignite ignite) {
    this.ignite = ignite;
  }

  public Barrier barrier(String name, int count) {
    return new Barrier(ignite, count, name);
  }

  public AtomicCounter atomicCounter(String name, long initialValue) {
    return new AtomicCounter(ignite, name, initialValue);
  }

}
