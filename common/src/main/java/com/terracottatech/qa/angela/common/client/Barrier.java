package com.terracottatech.qa.angela.common.client;

import org.apache.ignite.IgniteCountDownLatch;

public class Barrier {
  private final IgniteCountDownLatch countDownLatch;

  Barrier(IgniteCountDownLatch countDownLatch) {
    this.countDownLatch = countDownLatch;
  }

  public void await() {
    countDownLatch.countDown();
    if (countDownLatch.count() > 0) {
      countDownLatch.await();
    }
  }

}
