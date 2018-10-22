package com.terracottatech.qa.angela.common.clientconfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Aurelien Broszniowski
 */

public class ClientHostname {

  private String hostname;
  private AtomicInteger hostsCount;

  public ClientHostname(String hostname) {
    this.hostname = hostname;
    this.hostsCount = new AtomicInteger(1);
  }

  public String getHostname() {
    return hostname;
  }

  public AtomicInteger getHostsCount() {
    return hostsCount;
  }
}
