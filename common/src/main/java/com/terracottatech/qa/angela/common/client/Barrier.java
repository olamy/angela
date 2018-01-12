package com.terracottatech.qa.angela.common.client;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;
import org.apache.ignite.IgniteCountDownLatch;

public class Barrier {
  private final Ignite ignite;
  private final int count;
  private final int index;
  private final String name;
  private volatile IgniteCountDownLatch countDownLatch;
  private volatile int resetCount;

  Barrier(Ignite ignite, final int count, final String name) {
    this.ignite = ignite;
    this.count = count;
    IgniteAtomicLong igniteCounter = ignite.atomicLong(name, 0, true);
    this.index = (int)igniteCounter.getAndIncrement();
    igniteCounter.compareAndSet(count,0);
    this.name = name;
    resetLatch();
  }

  private void resetLatch() {
    countDownLatch = ignite.countDownLatch(name + "_" + (resetCount++), count, true, true);
  }

  public int await() {
    int countDown = countDownLatch.countDown();
    if (countDown > 0) {
      countDownLatch.await();
    }
    resetLatch();
    return index;
  }

}
