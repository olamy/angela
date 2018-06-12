package com.terracottatech.qa.angela.common.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ServiceLoader;

public class DisruptionProviderFactory {
  private static final DisruptionProvider DEFAULT_PROVIDER;

  static {
    DEFAULT_PROVIDER = loadDefault();
  }

  public static DisruptionProvider getDefault() {
    return DEFAULT_PROVIDER;
  }

  private static DisruptionProvider loadDefault() {
    ServiceLoader<DisruptionProvider> serviceLoader = ServiceLoader.load(DisruptionProvider.class);
    Collection<DisruptionProvider> providers = new ArrayList<>();
    for (DisruptionProvider provider : serviceLoader) {
      providers.add(provider);
    }

    if (providers.size() != 1) {
      throw new RuntimeException("Expected number of disrution provider is 1 but found " + providers.size());
    } else {
      return providers.iterator().next();
    }
  }
}
