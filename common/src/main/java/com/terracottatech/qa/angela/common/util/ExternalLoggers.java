package com.terracottatech.qa.angela.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalLoggers {

  private static final String PREFIX = "com.terracottatech.qa.angela.external";

  public static final Logger clusterToolLogger = LoggerFactory.getLogger(PREFIX + ".cluster-tool");
  public static final Logger clientLogger = LoggerFactory.getLogger(PREFIX + ".client");
  public static final Logger sshLogger = LoggerFactory.getLogger(PREFIX + ".ssh");
  public static final Logger tsaLogger = LoggerFactory.getLogger(PREFIX + ".tsa");
  public static final Logger tmsLogger = LoggerFactory.getLogger(PREFIX + ".tms");


}
