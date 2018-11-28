package com.terracottatech.qa.angela.common.cluster;

import com.terracottatech.qa.angela.common.clientconfig.ClientId;
import org.apache.ignite.Ignite;

public class Cluster {
  private final Ignite ignite;
  private final ClientId clientId;

  public Cluster(Ignite ignite) {
    this(ignite, null);
  }

  public Cluster(Ignite ignite, ClientId clientId) {
    this.ignite = ignite;
    this.clientId = clientId;
  }

  public Barrier barrier(String name, int count) {
    return new Barrier(ignite, count, name);
  }

  public AtomicCounter atomicCounter(String name, long initialValue) {
    return new AtomicCounter(ignite, name, initialValue);
  }

  public AtomicBoolean atomicBoolean(String name, boolean initialValue) {
    return new AtomicBoolean(ignite, name, initialValue);
  }

  public <T> AtomicReference<T> atomicReference(String name, T initialValue) {
    return new AtomicReference<>(ignite, name, initialValue);
  }

  /**
   * Returns the client ID if called in the context of a client job,
   * and null otherwise.
   */
  public ClientId getClientId() {
    return clientId;
  }
}
