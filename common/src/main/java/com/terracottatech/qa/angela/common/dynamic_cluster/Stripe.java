package com.terracottatech.qa.angela.common.dynamic_cluster;

import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Stripe {
  private final List<TerracottaServer> terracottaServerList;

  private Stripe(List<TerracottaServer> terracottaServerList) {
    this.terracottaServerList = terracottaServerList;
  }

  public static Stripe stripe(TerracottaServer... terracottaServers) {
    return new Stripe(Arrays.asList(terracottaServers));
  }

  public List<TerracottaServer> getTerracottaServerList() {
    return Collections.unmodifiableList(terracottaServerList);
  }
}
