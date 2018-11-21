package com.terracottatech.qa.angela.common.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;

public class AtomicBoolean {

  private final Ignite ignite;
  private final String name;
  private final IgniteAtomicLong igniteCounter;

  AtomicBoolean(Ignite ignite, String name, boolean initVal) {
    this.ignite = ignite;
    this.name = name;
    igniteCounter = ignite.atomicLong("Atomic-Boolean-" + name, initVal ? 1L : 0L, true);
  }

  public boolean get() {
    return igniteCounter.get() != 0L;
  }

  public void set(boolean value) {
    igniteCounter.getAndSet(value ? 1L : 0L);
  }

  public boolean getAndSet(boolean value) {
    return igniteCounter.getAndSet(value ? 1L : 0L) != 0L;
  }

  public boolean compareAndSet(boolean expVal, boolean newVal) {
    return igniteCounter.compareAndSet(expVal ? 1L : 0L, newVal ? 1L : 0L);
  }

  @Override
  public String toString() {
    return "" + get();
  }
}
