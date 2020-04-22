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

import java.util.Random;

public class RandomPortAllocator implements PortAllocator {
  private static final int LOWEST_PORT_INCLUSIVE = 1024;
  private static final int HIGHEST_PORT_INCLUSIVE = 32767;

  private final Random random;

  public RandomPortAllocator(Random random) {
    this.random = random;
  }

  @Override
  public int allocatePorts(int portCount) {
    int highestPortAdjustment = portCount - 1;
    return getRandomIntInRange(LOWEST_PORT_INCLUSIVE, HIGHEST_PORT_INCLUSIVE - highestPortAdjustment);
  }

  private int getRandomIntInRange(int min, int max) {
    return random.nextInt((max - min) + 1) + min;
  }
}
