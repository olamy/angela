package com.terracottatech.qa.angela.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ClientArrayFuture {
  private final Collection<Future<Void>> futures;

  ClientArrayFuture(Collection<Future<Void>> futures) {
    this.futures = futures;
  }

  public Collection<Future<Void>> getFutures() {
    return futures;
  }

  public void get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException {
    List<Exception> exceptions = new ArrayList<>();
    for (Future<Void> future : futures) {
      try {
        if (timeout == 0L && unit == null) {
          future.get();
        } else {
          future.get(timeout, unit);
        }
      } catch (Exception e) {
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
      } else {
        throw (InterruptedException) exception;
      }
    }
  }

  public void get() throws ExecutionException, InterruptedException {
    get(0L, null);
  }

  public boolean isDone() {
    return futures.stream()
        .map(Future::isDone)
        .reduce((b1, b2) -> b1 && b2)
        .orElse(true);
  }
}
