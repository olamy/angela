package com.terracottatech.qa.angela.common.net;

import java.net.InetSocketAddress;
import java.util.Objects;

class Link {
  private final InetSocketAddress source;
  private final InetSocketAddress destination;

  public Link(InetSocketAddress source, InetSocketAddress destination) {
    this.source = source;
    this.destination = destination;
  }

  public InetSocketAddress getSource() {
    return source;
  }

  public InetSocketAddress getDestination() {
    return destination;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Link link = (Link) o;
    return Objects.equals(source, link.source) &&
        Objects.equals(destination, link.destination);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, destination);
  }

  @Override
  public String toString() {
    return "Link{" +
        "source=" + source +
        ", destination=" + destination +
        '}';
  }
}
