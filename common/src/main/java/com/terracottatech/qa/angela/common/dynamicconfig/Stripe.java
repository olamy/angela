package com.terracottatech.qa.angela.common.dynamicconfig;

import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Stripe {
  private final List<TerracottaServer> terracottaServerList;

  private Stripe() {
    terracottaServerList = new ArrayList<>();
  }

  public static Stripe stripe(TerracottaServer... terracottaServers) {
    Stripe stripe = new Stripe();
    stripe.terracottaServerList.addAll(Arrays.asList(terracottaServers));
    return stripe;
  }

  public List<TerracottaServer> getTerracottaServerList() {
    return terracottaServerList;
  }
}
