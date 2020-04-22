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

public class LockingPortChooser {
  private final PortAllocator portAllocator;
  private final PortLocker portLocker;

  public LockingPortChooser(PortAllocator portAllocator, PortLocker portLocker) {
    this.portAllocator = portAllocator;
    this.portLocker = portLocker;
  }

  public synchronized MuxPortLock choosePorts(int portCount) {
    while (true) {
      MuxPortLock muxPortLock = tryChoosePorts(portCount);

      if (muxPortLock != null) {
        return muxPortLock;
      }
    }
  }

  private MuxPortLock tryChoosePorts(int portCount) {
    int portBase = portAllocator.allocatePorts(portCount);

    MuxPortLock muxPortLock = new MuxPortLock(portBase);

    for (int i = 0; i < portCount; i++) {
      int port = portBase + i;

      PortLock portLock = portLocker.tryLockPort(port);

      if (portLock == null) {
        muxPortLock.close();
        return null;
      }

      muxPortLock.addPortLock(portLock);
    }

    return muxPortLock;
  }
}
