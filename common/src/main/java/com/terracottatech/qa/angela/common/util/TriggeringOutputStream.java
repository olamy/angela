package com.terracottatech.qa.angela.common.util;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TriggeringOutputStream extends LogOutputStream {

  private final Consumer<String> consumer;

  public static final TriggeringOutputStream triggerOn(Pattern pattern, Consumer<MatchResult> action) {
    return new TriggeringOutputStream(line -> {
      Matcher matcher = pattern.matcher(line);
      if (matcher.matches()) {
        action.accept(matcher.toMatchResult());
      }
    });
  }

  public final TriggeringOutputStream andTriggerOn(Pattern pattern, Consumer<MatchResult> action) {
    return new TriggeringOutputStream(
        line -> {
          try {
            consumer.accept(line);
          } finally {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
              action.accept(matcher.toMatchResult());
            }
          }
    });
  }

  private TriggeringOutputStream(Consumer<String> consumer) {
    this.consumer = consumer;
  }

  @Override
  protected void processLine(final String line) {
    consumer.accept(line);
  }
}
