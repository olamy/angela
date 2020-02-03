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

package org.terracotta.angela.common.cluster;

import org.terracotta.angela.common.clientconfig.ClientId;
import org.apache.ignite.Ignite;

public class Cluster {
  private final Ignite ignite;
  private final ClientId clientId;

  public Cluster(Ignite ignite) {
    this(ignite, null);
  }

  public Cluster(Ignite ignite, ClientId clientId) {
    this.ignite = ignite;
    this.clientId = clientId;
  }

  public Barrier barrier(String name, int count) {
    return new Barrier(ignite, count, name);
  }

  public AtomicCounter atomicCounter(String name, long initialValue) {
    return new AtomicCounter(ignite, name, initialValue);
  }

  public AtomicBoolean atomicBoolean(String name, boolean initialValue) {
    return new AtomicBoolean(ignite, name, initialValue);
  }

  public <T> AtomicReference<T> atomicReference(String name, T initialValue) {
    return new AtomicReference<>(ignite, name, initialValue);
  }

  /**
   * @return the client ID if called in the context of a client job,
   * and null otherwise.
   */
  public ClientId getClientId() {
    return clientId;
  }
}
