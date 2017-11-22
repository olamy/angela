package com.terracottatech.qa.angela.common.client;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCountDownLatch;

public class Context {
  private final String nodeName;
  private final Ignite ignite;

  public Context(String nodeName, Ignite ignite) {
    this.nodeName = nodeName;
    this.ignite = ignite;
  }

  public Barrier barrier(String name, int count) {
    IgniteCountDownLatch countDownLatch = ignite.countDownLatch(nodeName + "@" + name, count, true, true);
    return new Barrier(countDownLatch);
  }

}
