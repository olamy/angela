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

import org.terracotta.angela.common.AngelaProperties;

/**
 * @author Mathieu Carbou
 */
public interface PortProvider {
  PortProvider SYS_PROPS = new PortProvider() {
    @Override
    public int getIgnitePort() {
      return Integer.parseInt(AngelaProperties.PORT.getValue());
    }

    @Override
    public int getIgnitePortRange() {
      return Integer.parseInt(AngelaProperties.PORT_RANGE.getValue());
    }

    @Override
    public int getNewRandomFreePort() {
      return PortChooser.chooseRandomPort();
    }

    @Override
    public int getNewRandomFreePorts(int count) {
      return PortChooser.chooseRandomPorts(count);
    }
  };

  int getIgnitePort();

  int getIgnitePortRange();

  default int getNewRandomFreePort() {
    return getNewRandomFreePorts(1);
  }

  int getNewRandomFreePorts(int count);
}
