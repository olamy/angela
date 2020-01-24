package com.terracottatech.qa.angela.common.util;

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
