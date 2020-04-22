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

import org.terracotta.angela.common.net.port_locking.LockingPortChooser;
import org.terracotta.angela.common.net.port_locking.LockingPortChoosers;
import org.terracotta.angela.common.net.port_locking.MuxPortLock;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Mathieu Carbou
 */
public class DefaultPortAllocator implements PortAllocator {

  private final LockingPortChooser lockingPortChooser = LockingPortChoosers.getFileLockingPortChooser();
  private final Collection<MuxPortLock> portLocks = new CopyOnWriteArrayList<>();

  @Override
  public PortAllocation getNewRandomFreePorts(int count) {
    MuxPortLock muxPortLock = lockingPortChooser.choosePorts(count);
    portLocks.add(muxPortLock);
    return new PortAllocation() {
      @Override
      public int getBasePort() {
        return muxPortLock.getPort();
      }

      @Override
      public int getPortCount() {
        return count;
      }

      @Override
      public void close() {
        if (portLocks.remove(muxPortLock)) {
          muxPortLock.close();
        }
      }
    };
  }

  @Override
  public void close() {
    while (!portLocks.isEmpty() && !Thread.currentThread().isInterrupted()) {
      for (MuxPortLock muxPortLock : portLocks) {
        if (portLocks.remove(muxPortLock)) {
          muxPortLock.close();
        }
      }
    }
  }
}
