package com.terracottatech.qa.angela.common.net;

/**
 * Base interface for all network disruptors(socket endpoint to endpoint or
 * client to servers or servers to servers)
 */
public interface Disruptor extends AutoCloseable {

  /**
   * shutdown traffic(partition)
   */
  void disrupt();


  /**
   * stop current disruption to restore back to original state
   */
  void undisrupt();


}
