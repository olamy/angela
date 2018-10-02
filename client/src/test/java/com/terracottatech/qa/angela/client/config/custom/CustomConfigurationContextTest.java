package com.terracottatech.qa.angela.client.config.custom;

import org.junit.Test;

import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.common.tcconfig.License;

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
