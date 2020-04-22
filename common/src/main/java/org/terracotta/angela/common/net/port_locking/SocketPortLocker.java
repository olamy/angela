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

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;

public class SocketPortLocker implements PortLocker {
  @Override
  public PortLock tryLockPort(int port) {
    ServerSocket serverSocket = null;

    try {
      serverSocket = new ServerSocket(port);
      return new EmptyPortLock(port);
    } catch (BindException be) {
      return null;
    } catch (IOException e) {
      throw new PortLockingException("Error detecting whether port " + port + " is available to bind", e);
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          throw new PortLockingException("Failed to close socket", e);
        }
      }
    }
  }
}
