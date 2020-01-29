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

package org.terracotta.angela.common.util;

import java.util.concurrent.Callable;

public class RetryUtils {
  /**
   * Waits for {@code maxRetryCount} time for the {@code condition} to be true.
   *
   * @param condition the condition to evaluate
   * @param maxWaitTimeMillis   the maximum time in milliseconds to wait before giving up
   * @return {@code true} if the condition was evaluated to true within the given constraints, false otherwise
   */
  public static boolean waitFor(Callable<Boolean> condition, int maxWaitTimeMillis) {
    int currentRetryAttempt = 0;
    int totalBackOffMillis = 0;
    while (totalBackOffMillis < maxWaitTimeMillis) {
      currentRetryAttempt++;
      try {
        if (!condition.call()) {
          totalBackOffMillis += 100 * currentRetryAttempt;
          Thread.sleep(totalBackOffMillis);
        } else {
          return true;
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return false;
  }
}
