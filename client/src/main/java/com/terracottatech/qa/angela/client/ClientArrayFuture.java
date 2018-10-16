package com.terracottatech.qa.angela.client;

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
        exception.addSuppressed(exceptions.get(i));
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

  public void get() throws CancellationException, ExecutionException, InterruptedException, TimeoutException {
    get(Long.MIN_VALUE, null);
  }

  public void cancel(boolean mayInterruptIfRunning) {
    futures.forEach(f -> f.cancel(mayInterruptIfRunning));
  }

  public boolean isDone() {
    return futures.stream()
        .map(Future::isDone)
        .reduce((b1, b2) -> b1 && b2)
        .orElse(true);
  }
}
