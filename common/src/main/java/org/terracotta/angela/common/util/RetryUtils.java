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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class RetryUtils {
  private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

  /**
   * Waits for {@code maxRetryCount} time for the {@code condition} to be true.
   *
   * @param condition         the condition to evaluate
   * @param maxWaitTimeMillis the maximum time in milliseconds to wait before giving up
   * @return {@code true} if the condition was evaluated to true within the given constraints, false otherwise
   */
  public static boolean waitFor(Callable<Boolean> condition, int maxWaitTimeMillis) {
    int currRetryTime = min(maxWaitTimeMillis, 100);
    int timeRemaining = maxWaitTimeMillis;
    ExecutorService executorService = Executors.newSingleThreadExecutor(Thread::new);
    boolean success = false;

    while (currRetryTime <= maxWaitTimeMillis) {
      long timeout = min(currRetryTime, timeRemaining); //Keep track of how much time remains
      Future<Boolean> future = executorService.submit(condition);
      if (getResult(timeout, future)) {
        logger.info("Condition became true after {}ms ", currRetryTime);
        success = true;
        break;
      } else {
        timeRemaining -= currRetryTime;
        currRetryTime *= 2; //Double retry timeout on each failure
      }
    }

    cleanup(executorService);
    return success;
  }

  private static Boolean getResult(long timeout, Future<Boolean> future) {
    Boolean result;
    try {
      result = future.get(timeout, MILLISECONDS);
      return result;
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return false;
    }
  }

  private static void cleanup(ExecutorService executorService) {
    try {
      executorService.shutdownNow();
      executorService.awaitTermination(1, MILLISECONDS);
    } catch (InterruptedException e) {
      // Ignore
    }
  }
}
