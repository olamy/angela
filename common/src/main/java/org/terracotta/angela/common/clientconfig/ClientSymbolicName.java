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

package org.terracotta.angela.common.clientconfig;

import java.util.Objects;

/**
 * @author Aurelien Broszniowski
 */

public class ClientSymbolicName {

  private final String symbolicName;

  public ClientSymbolicName(final String symbolicName) {
    Objects.requireNonNull(symbolicName, "symbolicName cannot be null");
    this.symbolicName = symbolicName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClientSymbolicName that = (ClientSymbolicName) o;

    return symbolicName.equals(that.symbolicName);
  }

  @Override
  public int hashCode() {
    return symbolicName.hashCode();
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  @Override
  public String toString() {
    return "ClientSymbolicName{" + symbolicName + '}';
  }

}
