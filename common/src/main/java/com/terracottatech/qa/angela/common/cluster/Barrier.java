package com.terracottatech.qa.angela.common.cluster;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;
import org.apache.ignite.IgniteCountDownLatch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Barrier {
  private final Ignite ignite;
  private final int count;
  private final int index;
  private final String name;
  private volatile IgniteCountDownLatch countDownLatch;
  private volatile int resetCount;

  Barrier(Ignite ignite, int count, String name) {
    this.ignite = ignite;
    this.count = count;
    IgniteAtomicLong igniteCounter = ignite.atomicLong("Barrier-Counter-" + name, 0, true);
    this.index = (int) igniteCounter.getAndIncrement();
    igniteCounter.compareAndSet(count, 0);
    this.name = name;
    resetLatch();
  }

  private void resetLatch() {
    countDownLatch = ignite.countDownLatch("Barrier-" + name + "#" + (resetCount++), count, true, true);
  }

  public int await() {
    int countDown = countDownLatch.countDown();
    try {
      if (countDown > 0) {
        countDownLatch.await();
      }
      return index;
    } finally {
      resetLatch();
    }
  }

  public int await(long time, TimeUnit unit) throws TimeoutException {
    int countDown = countDownLatch.countDown();
    try {
      if (countDown > 0) {
        if (!countDownLatch.await(time, unit)) {
          throw new TimeoutException();
        }
      }
      return index;
    } finally {
      resetLatch();
    }
  }

}
