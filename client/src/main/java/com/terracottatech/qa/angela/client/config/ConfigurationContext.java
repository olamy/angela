package com.terracottatech.qa.angela.client.config;

import java.util.Set;

public interface ConfigurationContext {

  RemotingConfigurationContext remoting();

  TsaConfigurationContext tsa();

  TmsConfigurationContext tms();

  ClientArrayConfigurationContext clientArray();

  Set<String> allHostnames();

}
