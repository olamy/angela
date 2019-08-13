package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Aurelien Broszniowski
 */

public class TsaTest {

  @Test
  public void testUrl10x() {
    TcConfig tcConfig = mock(TcConfig.class);
    License license = mock(License.class);
    TsaConfigurationContext tsaConfigurationContext = mock(TsaConfigurationContext.class);
    when(tsaConfigurationContext.getTopology()).then(invocationOnMock -> new Topology(distribution(version("10.3.0.0.0"), PackageType.KIT, LicenseType.TERRACOTTA), tcConfig));
    when(tsaConfigurationContext.getLicense()).thenReturn(license);
    Tsa tsa = new Tsa(null, null, tsaConfigurationContext);
    List<TerracottaServer> terracottaServerList = new ArrayList<>();
    terracottaServerList.add(new TerracottaServer("1", "hostname1", 9510, 9610, 9810, 9910));
    terracottaServerList.add(new TerracottaServer("2", "hostname2", 9511, 9611, 9811, 9911));

    when(tcConfig.getServers()).thenReturn(terracottaServerList);

    final URI uri = tsa.uri();
    assertThat(uri.toString(), is("terracotta://hostname1:9510,hostname2:9511"));

  }

  @Test
  public void testUrl4x() {
    TcConfig tcConfig = mock(TcConfig.class);
    License license = mock(License.class);
    TsaConfigurationContext tsaConfigurationContext = mock(TsaConfigurationContext.class);
    when(tsaConfigurationContext.getTopology()).then(invocationOnMock -> new Topology(distribution(version("4.3.6.0.0"), PackageType.KIT, LicenseType.TERRACOTTA), tcConfig));
    when(tsaConfigurationContext.getLicense()).thenReturn(license);
    Tsa tsa = new Tsa(null, null, tsaConfigurationContext);
    List<TerracottaServer> terracottaServerList = new ArrayList<>();
    terracottaServerList.add(new TerracottaServer("1", "hostname1", 9510, 9610, 9810, 9910));
    terracottaServerList.add(new TerracottaServer("2", "hostname2", 9511, 9611, 9811, 9911));

    when(tcConfig.getServers()).thenReturn(terracottaServerList);

    final URI uri = tsa.uri();
    assertThat(uri.toString(), is("hostname1:9510,hostname2:9511"));

  }
}
