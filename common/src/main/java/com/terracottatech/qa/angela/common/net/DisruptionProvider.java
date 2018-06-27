package com.terracottatech.qa.angela.common.net;

import java.net.InetSocketAddress;

/**
 *
 *
 */
public interface DisruptionProvider {


  /**
   * Proxy based provider such as netcrusher or toxiproxy to return true here.
   *
   * @return
   */
  boolean isProxyBased();

  /**
   * Create link to disrupt traffic flowing from the given source address to destination address(unidirectional)
   *
   * @param src
   * @param dest
   * @return
   */
  Disruptor createLink(InetSocketAddress src, InetSocketAddress dest);


  /**
   * remove link
   *
   * @param link
   */
  void removeLink(Disruptor link);

}
