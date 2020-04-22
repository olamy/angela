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
import java.io.RandomAccessFile;
import java.nio.channels.Channel;
import java.nio.channels.FileLock;

public class GlobalFilePortLock implements PortLock {
  private final int port;
  private final RandomAccessFile file;
  private final Channel channel;
  private final FileLock lock;

  GlobalFilePortLock(int port, RandomAccessFile file, Channel channel, FileLock lock) {
    this.port = port;
    this.file = file;
    this.channel = channel;
    this.lock = lock;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void close() {
    PortLockingException closeError = new PortLockingException("Failed to unlock during close");

    try {
      lock.close();
    } catch (IOException e) {
      closeError.addSuppressed(e);
    }

    try {
      channel.close();
    } catch (IOException e) {
      closeError.addSuppressed(e);
    }

    try {
      file.close();
    } catch (IOException e) {
      closeError.addSuppressed(e);
    }

    if (closeError.getSuppressed().length > 0) {
      throw closeError;
    }
  }
}