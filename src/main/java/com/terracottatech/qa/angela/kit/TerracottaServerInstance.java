package com.terracottatech.qa.angela.kit;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terracotta server instance
 *
 * @author Aurelien Broszniowski
 */
public class TerracottaServerInstance  {

  private static final Logger logger = LoggerFactory.getLogger(TerracottaServerInstance.class);

  private TerracottaServerState state = TerracottaServerState.STOPPED;

  public TerracottaServerState getState() {
    return state;
  }

  public void setState(final TerracottaServerState state) {
    this.state = state;
  }

  public enum TerracottaServerState {
    STOPPED,
    STARTED_AS_ACTIVE,
    STARTED_AS_PASSIVE,
    PAUSED,
  }

}
