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

import java.security.SecureRandom;

public class LockingPortChoosers {

  private static final LockingPortChooser SINGLETON = new LockingPortChooser(
      new RandomPortAllocator(new SecureRandom()),
      new MuxPortLocker(
          new LocalPortLocker(),
          new SocketPortLocker(),
          new GlobalFilePortLocker()
      )
  );

  /**
   * Returns a LockingPortChooser that works by acquiring a lock for a port by creating a FileLock on the corresponding
   * byte in an agreed upon file. The FileLocks are created by GlobalFilePortLocker.
   * Because FileLocks are held by the whole JVM, we also need a system of locking within this JVM, which is handled by
   * the LocalPortLocker.
   * In the SocketPortLocker, we also check that the socket is available.
   *
   * @return the LockingPortChooser that can generate ports that should be free to use as long as the lock is held
   */
  public static LockingPortChooser getFileLockingPortChooser() {
    return SINGLETON;
  }
}
