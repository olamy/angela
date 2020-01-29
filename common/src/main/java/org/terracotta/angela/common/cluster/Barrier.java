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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicLong;
import org.apache.ignite.IgniteCountDownLatch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Barrier {
  private final Ignite ignite;
  private final int count;
  private final int index;
  private final String name;
  private volatile IgniteCountDownLatch countDownLatch;
  private volatile int resetCount;

  Barrier(Ignite ignite, int count, String name) {
    this.ignite = ignite;
    this.count = count;
    IgniteAtomicLong igniteCounter = ignite.atomicLong("Barrier-Counter-" + name, 0, true);
    this.index = (int) igniteCounter.getAndIncrement();
    igniteCounter.compareAndSet(count, 0);
    this.name = name;
    resetLatch();
  }

  private void resetLatch() {
    countDownLatch = ignite.countDownLatch("Barrier-" + name + "#" + (resetCount++), count, true, true);
  }

  public int await() {
    int countDown = countDownLatch.countDown();
    try {
      if (countDown > 0) {
        countDownLatch.await();
      }
      return index;
    } finally {
      resetLatch();
    }
  }

  public int await(long time, TimeUnit unit) throws TimeoutException {
    int countDown = countDownLatch.countDown();
    try {
      if (countDown > 0) {
        if (!countDownLatch.await(time, unit)) {
          throw new TimeoutException();
        }
      }
      return index;
    } finally {
      resetLatch();
    }
  }

}
