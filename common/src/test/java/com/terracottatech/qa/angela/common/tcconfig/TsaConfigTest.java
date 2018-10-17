package com.terracottatech.qa.angela.common.tcconfig;

import org.junit.Test;

import com.terracottatech.qa.angela.common.topology.Version;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Aurelien Broszniowski
 */

public class TsaConfigTest {

  @Test
  public void testAddServer() {
    final TsaConfig tsaConfig = new TsaConfig().stripes(Version.version("10.0.0.0.0"), "host1", "host2");
    final TcConfig[] tcConfigs = tsaConfig.getTcConfigs();
    assertThat(tcConfigs.length, equalTo(1));
    final Collection<TerracottaServer> servers = tcConfigs[0].getServers().values();
    assertThat(servers.size(), equalTo(2));
    final Iterator<TerracottaServer> iterator = servers.iterator();
    assertThat(iterator.next().getServerSymbolicName().getSymbolicName(), is("Server1-1"));
    assertThat(iterator.next().getServerSymbolicName().getSymbolicName(), is("Server1-2"));


  }
}
