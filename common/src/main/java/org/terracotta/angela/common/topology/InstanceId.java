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

package org.terracotta.angela.common.topology;

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class InstanceId {
  private final String prefix;
  private final String type;

  public InstanceId(String idPrefix, String type) {
    this.prefix = Objects.requireNonNull(idPrefix).replaceAll("[^a-zA-Z0-9.-]", "_");;
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InstanceId that = (InstanceId) o;
    return Objects.equals(prefix, that.prefix) &&
        Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefix, type);
  }

  @Override
  public String toString() {
    return String.format("%s-%s", prefix, type);
  }

}
