package com.terracottatech.qa.angela.client.config;

public interface ConfigurationContext {

  RemotingConfigurationContext remoting();

  TsaConfigurationContext tsa();

  TmsConfigurationContext tms();

  ClientArrayConfigurationContext clientArray();

}
