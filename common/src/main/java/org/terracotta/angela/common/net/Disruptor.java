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
