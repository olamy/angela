package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.ClientArrayConfigurationContext;
import com.terracottatech.qa.angela.client.config.TmsConfigurationContext;
import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CustomMultiConfigurationContext extends CustomConfigurationContext {

  public static CustomMultiConfigurationContext customMultiConfigurationContext() {
    return new CustomMultiConfigurationContext();
  }

  private CustomMultiConfigurationContext() {
  }

  private final List<CustomTsaConfigurationContext> customTsaConfigurationContexts = new ArrayList<>();
  private int customTsaConfigurationContextsIndex = 0;
  private final List<CustomTmsConfigurationContext> customTmsConfigurationContexts = new ArrayList<>();
  private int customTmsConfigurationContextsIndex = 0;
  private final List<CustomClientArrayConfigurationContext> customClientArrayConfigurationContexts = new ArrayList<>();
  private int customClientArrayConfigurationContextsIndex = 0;

  @Override
  public TsaConfigurationContext tsa() {
    if (customTsaConfigurationContextsIndex >= customTsaConfigurationContexts.size()) {
      throw new IllegalStateException(customTsaConfigurationContexts.size() + " contained TSA configs, but trying to access config #" + customTsaConfigurationContextsIndex);
    }
    return customTsaConfigurationContexts.get(customTsaConfigurationContextsIndex++);
  }

  @Override
  public CustomConfigurationContext tsa(Consumer<CustomTsaConfigurationContext> tsa) {
    CustomTsaConfigurationContext customTsaConfigurationContext = new CustomTsaConfigurationContext();
    customTsaConfigurationContexts.add(customTsaConfigurationContext);
    tsa.accept(customTsaConfigurationContext);
    return this;
  }

  @Override
  public TmsConfigurationContext tms() {
    if (customTmsConfigurationContextsIndex >= customTmsConfigurationContexts.size()) {
      throw new IllegalStateException(customTmsConfigurationContexts.size() + " contained TMS configs, but trying to access config #" + customTmsConfigurationContextsIndex);
    }
    return customTmsConfigurationContexts.get(customTmsConfigurationContextsIndex++);
  }

  @Override
  public CustomConfigurationContext tms(Consumer<CustomTmsConfigurationContext> tms) {
    CustomTmsConfigurationContext customTmsConfigurationContext = new CustomTmsConfigurationContext();
    customTmsConfigurationContexts.add(customTmsConfigurationContext);
    tms.accept(customTmsConfigurationContext);
    return this;
  }

  @Override
  public ClientArrayConfigurationContext clientArray() {
    if (customClientArrayConfigurationContextsIndex >= customClientArrayConfigurationContexts.size()) {
      throw new IllegalStateException(customClientArrayConfigurationContexts.size() + " contained client array configs, but trying to access config #" + customClientArrayConfigurationContextsIndex);
    }
    return customClientArrayConfigurationContexts.get(customClientArrayConfigurationContextsIndex++);
  }

  @Override
  public CustomConfigurationContext clientArray(Consumer<CustomClientArrayConfigurationContext> clientArray) {
    CustomClientArrayConfigurationContext customClientArrayConfigurationContext = new CustomClientArrayConfigurationContext();
    customClientArrayConfigurationContexts.add(customClientArrayConfigurationContext);
    clientArray.accept(customClientArrayConfigurationContext);
    return this;
  }
}
