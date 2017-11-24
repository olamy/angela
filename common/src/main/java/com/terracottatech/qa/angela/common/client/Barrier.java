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
  private volatile int i;

  Barrier(Ignite ignite, final int count, final String name) {
    this.ignite = ignite;
    this.count = count;
    IgniteAtomicLong igniteCounter = ignite.atomicLong(name, 0, true);
    this.index = (int)igniteCounter.getAndIncrement();
    igniteCounter.compareAndSet(count - 1, 0);
    this.name = name;
    resetLatch();
  }

  private void resetLatch() {
    countDownLatch = ignite.countDownLatch(name + i++, count, true, true);
  }

  public int await() {
    System.out.println("" + Thread.currentThread().getName() + "  awaiting... ");
    int countDown = countDownLatch.countDown();
    System.out.println("" + Thread.currentThread().getName() + "  countdown = " + countDown);
    if (countDown > 0) {
      System.out.println("" + Thread.currentThread().getName() + "  before await ");
      countDownLatch.await();
      System.out.println("" + Thread.currentThread().getName() + "  after await ");
    }
    System.out.println("" + Thread.currentThread().getName() + "  before resetLatch ");
    resetLatch();
    System.out.println("" + Thread.currentThread().getName() + "  after resetLatch ");
    return index;
  }

}
