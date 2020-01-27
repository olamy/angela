/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.net;

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
