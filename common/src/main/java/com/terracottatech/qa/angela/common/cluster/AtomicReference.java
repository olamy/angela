package com.terracottatech.qa.angela.common.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicReference;

public class AtomicReference<T> {

  private final IgniteAtomicReference<T> igniteReference;

  AtomicReference(Ignite ignite, String name, T initialValue) {
    igniteReference = ignite.atomicReference("Atomic-Reference-" + name, initialValue, true);
  }

  public void set(T value) {
    igniteReference.set(value);
  }

  public boolean compareAndSet(T expect, T update) {
    return igniteReference.compareAndSet(expect, update);
  }

  public T get() {
    return igniteReference.get();
  }
}
