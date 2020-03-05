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

import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.terracotta.angela.common.util.RetryUtils.waitFor;

public class RetryUtilsTest {
  @Test
  public void testConditionTrue_smallDuration() {
    assertTrue(waitFor(() -> true, 10));
  }

  @Test
  public void testConditionTrue_largeDuration() {
    assertTrue(waitFor(() -> true, 5000));
  }

  @Test
  public void testConditionTrue_afterFixedTime() {
    Callable<Boolean> callable = () -> {
      Thread.sleep(500);
      return true;
    };
    assertTrue(waitFor(callable, 5000));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testConditionTrue_afterFewTries() throws Exception {
    Callable<Boolean> callable = (Callable<Boolean>) mock(Callable.class);
    when(callable.call()).thenReturn(false).thenReturn(false).thenReturn(false).thenReturn(false).thenReturn(true);
    assertTrue(waitFor(callable, 5000));
  }

  @Test
  public void testConditionFalse_smallDuration() {
    assertFalse(waitFor(() -> false, 10));
  }

  @Test
  public void testConditionFalse_largeDuration() {
    assertFalse(waitFor(() -> false, 5000));
  }

  @Test
  public void testConditionFalse_afterFixedTime() {
    Callable<Boolean> callable = () -> {
      Thread.sleep(500);
      return false;
    };
    assertFalse(waitFor(callable, 5000));
  }
}
