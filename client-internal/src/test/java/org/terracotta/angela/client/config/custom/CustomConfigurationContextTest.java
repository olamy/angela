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

package org.terracotta.angela.client.config.custom;

import org.junit.Test;

import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.client.config.custom.CustomConfigurationContext;
import org.terracotta.angela.common.tcconfig.License;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author Aurelien Broszniowski
 */

public class CustomConfigurationContextTest {

  @Test
  public void testTsaWithoutTopology() {
    try {
      ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
          .tsa(tsa -> {
                final License license = mock(License.class);
                tsa.license(license);
              }
          );
      fail("Exception due to lack of Topology should have been thrown");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
}
