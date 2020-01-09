package com.terracottatech.qa.angela.common.dynamic_cluster;

import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Stripe {
  private final List<TerracottaServer> servers;

  private Stripe(TerracottaServer... servers) {
    if (servers == null || servers.length == 0) {
      throw new IllegalArgumentException("Server list cannot be null or empty");
    }
    this.servers = new ArrayList<>(Arrays.asList(servers));
  }

  public static Stripe stripe(TerracottaServer... terracottaServers) {
    return new Stripe(terracottaServers);
  }

  public void addServer(TerracottaServer newServer) {
    servers.add(newServer);
  }

  public void removeServer(int serverIndex) {
    servers.remove(serverIndex);
  }

  public TerracottaServer getServer(int serverIndex) {
    return servers.get(serverIndex);
  }

  public List<TerracottaServer> getServers() {
    return Collections.unmodifiableList(servers);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Stripe stripe = (Stripe) o;
    return servers.equals(stripe.servers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(servers);
  }

  @Override
  public String toString() {
    return "Stripe{" +
        "servers=" + servers +
        '}';
  }
}
