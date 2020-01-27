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

package org.terracotta.angela.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientArrayFuture {
  private final Collection<Future<Void>> futures;

  ClientArrayFuture(Collection<Future<Void>> futures) {
    this.futures = futures;
  }

  public Collection<Future<Void>> getFutures() {
    return futures;
  }

  public void get(long timeout, TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException, TimeoutException {
    List<Exception> exceptions = new ArrayList<>();
    for (Future<Void> future : futures) {
      try {
        if (timeout == Long.MIN_VALUE && unit == null) {
          future.get();
        } else {
          future.get(timeout, unit);
        }
      } catch (RuntimeException | ExecutionException | InterruptedException | TimeoutException e) {
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      Exception exception = exceptions.get(0);
      for (int i = 1; i < exceptions.size(); i++) {
        Throwable t = exceptions.get(i);
        if (t instanceof ExecutionException) {
          t = t.getCause();
        }
        if (t instanceof Client.RemoteExecutionException) {
          ((Client.RemoteExecutionException) t).setRemoteStackTraceIndentation(2);
        }
        exception.addSuppressed(t);
      }
      if (exception instanceof RuntimeException) {
        throw (RuntimeException) exception;
      } else if (exception instanceof ExecutionException) {
        throw (ExecutionException) exception;
      } else if (exception instanceof InterruptedException) {
        throw (InterruptedException) exception;
      } else {
        throw (TimeoutException) exception;
      }
    }
  }

  public void get() throws CancellationException, ExecutionException, InterruptedException {
    try {
      get(Long.MIN_VALUE, null);
    } catch (TimeoutException te) {
      // This should never happen
      throw new RuntimeException(te);
    }
  }

  public void cancel(boolean mayInterruptIfRunning) {
    futures.forEach(f -> f.cancel(mayInterruptIfRunning));
  }

  public boolean isAnyDone() {
    return futures.stream()
        .map(Future::isDone)
        .reduce((b1, b2) -> b1 || b2)
        .orElse(true);
  }

  public boolean isAllDone() {
    return futures.stream()
        .map(Future::isDone)
        .reduce((b1, b2) -> b1 && b2)
        .orElse(true);
  }
}
