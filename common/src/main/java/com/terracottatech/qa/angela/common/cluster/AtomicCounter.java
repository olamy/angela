package com.terracottatech.qa.angela.common.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;

public class AtomicCounter {

  private final Ignite ignite;
  private final String name;
  private final IgniteAtomicLong igniteCounter;

  AtomicCounter(Ignite ignite, String name, long initVal) {
    this.ignite = ignite;
    this.name = name;
    igniteCounter = ignite.atomicLong("Atomic-Counter-" + name, initVal, true);
  }

  public long incrementAndGet() {
    return igniteCounter.incrementAndGet();
  }

  public long getAndIncrement() {
    return igniteCounter.getAndIncrement();
  }

  public long get() {
    return igniteCounter.get();
  }

  public long getAndSet(long value) {
    return igniteCounter.getAndSet(value);
  }

  public boolean compareAndSet(long expVal, long newVal) {
    return igniteCounter.compareAndSet(expVal, newVal);
  }

  @Override
  public String toString() {
    return "" + get();
  }
}
