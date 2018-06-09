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
  public boolean isProxyBased();

  /**
   * Create link to disrupt traffic flowing from the given source address to destination address(unidirectional)
   *
   * @param src
   * @param dest
   * @return
   */
  public Disruptor createLink(InetSocketAddress src, InetSocketAddress dest);


  /**
   * remove link
   *
   * @param link
   */
  public void removeLink(Disruptor link);

}
