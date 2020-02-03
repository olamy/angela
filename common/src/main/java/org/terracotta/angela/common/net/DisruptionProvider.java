/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.net;

import java.net.InetSocketAddress;

/**
 *
 *
 */
public interface DisruptionProvider {


  /**
   * @return true in case of a proxy based provider such as netcrusher or toxiproxy
   */
  boolean isProxyBased();

  /**
   * Create link to disrupt traffic flowing from the given source address to destination address(unidirectional)
   *
   * @param src source address
   * @param dest destination address
   * @return Disruptor link
   */
  Disruptor createLink(InetSocketAddress src, InetSocketAddress dest);


  /**
   * remove link
   *
   * @param link {@link Disruptor}
   */
  void removeLink(Disruptor link);

}
