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
package org.terracotta.angela.common.net.port_locking;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LocalPortLocker implements PortLocker {
  private static final Set<Integer> localPortLocks = ConcurrentHashMap.newKeySet();

  @Override
  public PortLock tryLockPort(int port) {
    boolean added = localPortLocks.add(port);

    if (!added) {
      return null;
    }

    return new LocalPortLock(port);
  }

  private static class LocalPortLock implements PortLock {
    private final int port;

    LocalPortLock(int port) {
      this.port = port;
    }

    @Override
    public int getPort() {
      return port;
    }

    @Override
    public void close() {
      boolean removed = localPortLocks.remove(port);

      if (!removed) {
        throw new AssertionError("Attempted to remove local lock on port " + port + " but it was not present");
      }
    }
  }
}
